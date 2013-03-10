package com.miyou.service.impl;

import com.miyou.service.RecentMiyouService;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Service;
import utils.UserDto;
import weibo4j.Friendships;
import weibo4j.model.User;
import weibo4j.model.UserWapper;
import weibo4j.model.WeiboException;

import java.util.*;
import java.util.concurrent.*;

/**
 * Created with IntelliJ IDEA.
 * User: chenxiaojian
 * Date: 13-3-9
 * Time: 上午11:01
 * To change this template use File | Settings | File Templates.
 */

@Service("RecentMiyouService")
public class RecentMiyouServiceImp implements RecentMiyouService {

    private final String ACCESS_TOKEN = "2.00K2RpoBUfU9QB7d8acaec42Ap9LlB";

    private final int DEFAULT_COUNT = 100;//使用新浪微博API批量接口默认置为至多100条


    /**
     * 获取和当前用户相同互粉好友最多的前count用户
     *
     * @param currentUserId 当前用户
     * @param count         个数
     */
    public List<User> getMutualFriendsByCount(String currentUserId, int count) {
        Friendships fm = new Friendships();
        fm.client.setToken(ACCESS_TOKEN);
        try {
            UserWapper userWapper = fm.getFriendsBilateral(currentUserId, DEFAULT_COUNT);

            Map<User, Integer> friendMapping = getFriendMapping(currentUserId, userWapper.getUsers());
            Set<Map.Entry<User, Integer>> entry = friendMapping.entrySet();
            List<Map.Entry<User, Integer>> totalMutualFriend = new ArrayList<Map.Entry<User, Integer>>(entry);
            if (totalMutualFriend == null) return null;
            Collections.sort(totalMutualFriend, new Comparator() {
                public int compare(Object o1, Object o2) {
                    return ((Comparable) ((Map.Entry<User, Integer>) o2).getValue()).compareTo(((Map.Entry<User, Integer>) o1).getValue());
                }
            }); //按照相同互粉好友的个数从大到小排序
            //for test
//            for (Map.Entry<User, Integer> entry1 : totalMutualFriend) {
//                System.out.println("userId: " + entry1.getKey().getScreenName() + "  count: " + entry1.getValue());
//            }
            List<User> mutualFriend = new ArrayList<User>();
            for (int i = 0; i < count && i < totalMutualFriend.size(); i++) {
                mutualFriend.add(totalMutualFriend.get(i).getKey());
            }
            return mutualFriend;
        } catch (WeiboException e) {
            e.printStackTrace();
        }
        return null;
    }


    /**
     * 获取我和我的互粉好友列表中各个好友拥有共同好友的个数
     *
     * @param myUserId  我的ID
     * @param myfriends 我的互粉好友列表
     */
    private Map<User, Integer> getFriendMapping(String myUserId, final List<User> myfriends) {
        Map<User, Integer> intimacyFriendsMapping = new HashMap<User, Integer>();
        if (StringUtils.isBlank(myUserId) || myfriends == null || myfriends.size() == 0)
            return null;
        int friendCount = myfriends.size();
        ExecutorService executor = Executors.newFixedThreadPool(friendCount);
        CompletionService completionService = new ExecutorCompletionService(executor);
        for (int i = 0; i < friendCount; i++) {
            final int num = i;
            completionService.submit(new Callable() {
                @Override
                public UserDto call() throws Exception {
                    try {
                        User myfriend = myfriends.get(num);
                        Integer matchFriendCount = matchCommonFriends(myfriends, myfriend.getId());
                        System.out.println("Thread:" + Thread.currentThread() + " userName:" + myfriend.getScreenName() + " matchCount:" + matchFriendCount);
                        UserDto myFriendDto = new UserDto();
                        myFriendDto.setUser(myfriend);
                        myFriendDto.setCount(matchFriendCount);
                        return myFriendDto;
                    } catch (Exception e) {

                    }
                    return null;
                }
            });

        }


        for (int i = 0; i < friendCount; i++) {
            Future<UserDto> future = null;
            try {
                future = completionService.take();
                try {
                    UserDto myFriendDto = future.get(60, TimeUnit.SECONDS);
                    intimacyFriendsMapping.put(myFriendDto.getUser(), myFriendDto.getCount());
                } catch (ExecutionException e) {

                } catch (TimeoutException e) {

                }
            } catch (InterruptedException e) {

            }
        }

        return intimacyFriendsMapping;
    }


    /**
     * 获得我的互粉好友列表与某互粉好友的互粉列表中共同好友的个数
     *
     * @param myfriends  我的互粉好友列表
     * @param myFriendId 我好友的ID
     */
    private Integer matchCommonFriends(List<User> myfriends, String myFriendId) {
        Friendships fm = new Friendships();
        fm.client.setToken(ACCESS_TOKEN);
        try {
            UserWapper myFriendUserWapper = fm.getFriendsBilateral(myFriendId, DEFAULT_COUNT);
            List<User> myFriendFriends = myFriendUserWapper.getUsers();
            Integer matchFriendCount = getMatchFriendCount(myFriendFriends, myfriends);
            return matchFriendCount;
        } catch (WeiboException e) {
            e.printStackTrace();
        }
        return null;
    }


    /**
     * 将我的互粉列表和我的某个好友的互粉列表进行match
     *
     * @param myFriendFriends 我的某个好友的互粉列表
     * @param myfriends       我的互粉列表
     */
    private Integer getMatchFriendCount(List<User> myFriendFriends, List<User> myfriends) {
        if (myFriendFriends == null || myfriends == null) return null;
        int count = 0;
        for (int i = 0; i < myfriends.size(); i++) {
            for (int j = 0; j < myFriendFriends.size(); j++) {
                if (myfriends.get(i).getId().equals(myFriendFriends.get(j).getId()))
                    count++;
            }
        }
        return count;
    }

    public static void main(String[] args) {
        //uid=1667171020
        String access_token = "2.00K2RpoBUfU9QB7d8acaec42Ap9LlB";
        String uid = "1667171020";
        RecentMiyouService recentMiyouService = new RecentMiyouServiceImp();
        List<User> recentMiyou = recentMiyouService.getMutualFriendsByCount(uid, 50);
        for (User user : recentMiyou) {
            System.out.println(user.getScreenName());
        }
    }

}

