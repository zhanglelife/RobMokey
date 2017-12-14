package com.doov.robmoney;

import android.accessibilityservice.AccessibilityService;
import android.annotation.SuppressLint;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.os.PowerManager;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.List;

public class RobMonkeyAccessibilityService extends AccessibilityService {
    private static final String TAG = "RobMonkeyAccessibilityService";

    /**
     * 是否可以点击红包
     */
    private boolean canGetMonkey = false;

    private boolean isGetMonkey = false;

    /*当前窗口状态*/
    private int mWindowStatus = WINDOW_NONE;

    /*各种窗口状态*/
    private final static int WINDOW_LANUCHRE = 1;
    private final static int WINDOW_RECEIVERUI = 2;
    private final static int WINDOW_DETAILUI = 3;
    private final static int WINDOW_OTHER = 4;
    private final static int WINDOW_NONE = 5;
    private final static int WINDOW_QQ_LANUCHRE = 6;


    private PowerManager mPowerManager;
    private KeyguardManager mKeyguardManager;
    private KeyguardManager.KeyguardLock mKeyguardLock;
    private PowerManager.WakeLock mWakeLock;

    private boolean canGetQQMonkey = true;

    private AccessibilityNodeInfo mLastMonkeyNode = null;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        int eventType = event.getEventType();
        Log.d(TAG, "onAccessibilityEvent: eventType=" + eventType);

        switch (eventType) {
            case AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED://64  有通知
                List<CharSequence> notifications = event.getText();
                if (notifications != null) {
                    for (CharSequence c : notifications) {
                        String s = c.toString();
                        Log.d(TAG, "onAccessibilityEvent: s=" + s);
                        if (s.contains("[微信红包]") || s.contains("[QQ红包]")) {
                            Notification notification = (Notification) event.getParcelableData();
                            PendingIntent pendingIntent = notification.contentIntent;
                            try {
                                wakeUpAndUnLock(true);//亮屏并解鎖
                                canGetMonkey = true;
                                pendingIntent.send();
                            } catch (PendingIntent.CanceledException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
                break;
            case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED://32
                String clazz = event.getClassName().toString();
                Log.d(TAG, "onAccessibilityEvent: clazz=" + clazz);

                if ("com.tencent.mm.ui.LauncherUI".equals(clazz)) {//微信消息列表页面
                    mWindowStatus = WINDOW_LANUCHRE;
                    getWeiXinPacket();
                } else if ("com.tencent.mm.plugin.luckymoney.ui.LuckyMoneyReceiveUI".equals(clazz)) {//微信抢红包页面
                    mWindowStatus = WINDOW_RECEIVERUI;
                    openWeiXinPacket();
                } else if ("com.tencent.mm.plugin.luckymoney.ui.LuckyMoneyDetailUI".equals(clazz)) {//微信已领取红包列表页面
                    mWindowStatus = WINDOW_DETAILUI;
                    //performBackClick();
                } else if ("com.tencent.mobileqq.activity.SplashActivity".equals(clazz)) {//QQ消息列表頁面
                    mWindowStatus = WINDOW_QQ_LANUCHRE;
                    //getQQPacket();
                } else if ("cooperation.qwallet.plugin.QWalletPluginProxyActivity".equals(clazz)) {//QQ紅包頁面
                    mWindowStatus = WINDOW_OTHER;
                    //performBackClick();
                } else {
                    mWindowStatus = WINDOW_OTHER;
                }
                break;
            case AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED://2048
                if (mWindowStatus == WINDOW_LANUCHRE) {//在消息列表页面,其他页面不处理
                    Log.d(TAG, "onAccessibilityEvent: canGetMonkey=" + canGetMonkey + " isGetMonkey=" + isGetMonkey);
                    if (isGetMonkey) {
                        return;
                    }
                    getWeiXinPacket();
                }
                if (mWindowStatus == WINDOW_QQ_LANUCHRE) {
                    if (canGetQQMonkey) openQQPacket();
                }
                break;
            default:
                break;
        }
    }

    /**
     * 打开QQ红包
     */
    private void openQQPacket() {
        Log.i(TAG, "openQQPacket: ");
        //com.tencent.mobileqq:id/name TextView 点击拆开
        AccessibilityNodeInfo rootNote = getRootInActiveWindow();
        if (rootNote != null) {
            List<AccessibilityNodeInfo> list = rootNote.findAccessibilityNodeInfosByText("QQ红包");
            if (list != null && !list.isEmpty()) {
                Log.d(TAG, "openQQPacket() list not null");
                for (AccessibilityNodeInfo i : list) {
                    AccessibilityNodeInfo parent = i.getParent();
                    if (null != findNodeInfosByText(parent, "点击拆开")) {
                        canGetQQMonkey = false;//打开红包时不去响应其他消息的事件
                        performClick(i.getParent());
                    }
                }
            }
        }

        canGetQQMonkey = true;
    }

    private void getQQPacket() {
        Log.i(TAG, "getQQPacket: ");
        AccessibilityNodeInfo rootNote = getRootInActiveWindow();
        if (rootNote != null) {
            AccessibilityNodeInfo recent_chat_list = findNodeInfosById(rootNote, "com.tencent.mobileqq:id/recent_chat_list");//消息列表页面
            if (recent_chat_list != null) {
               /* List<AccessibilityNodeInfo> list = recent_chat_list.findAccessibilityNodeInfosByText("[有红包][QQ红包]恭喜发财");
                if (list != null && !list.isEmpty()) {
                    Log.d(TAG, "getQQPacket() list not null");
                    AccessibilityNodeInfo note = list.get(list.size() -1);//最新的有红包的会话
                    performClick(note);
                }*/
                /*int count = recent_chat_list.getChildCount();
                for (int i = 0; i <count; i++) {
                    AccessibilityNodeInfo note = recent_chat_list.getChild(i);
                    Log.d(TAG, "getQQPacket:" + note.getText() + "--" + note.getViewIdResourceName());
                }*/
            }
        }

    }

    /**
     * 喚醒屏幕 解锁
     *
     * @param unlock
     */
    private void wakeUpAndUnLock(boolean unlock) {
        Log.d(TAG, "wakeUpAndUnLock() called with: unlock = [" + unlock + "]");

        if (unlock) {
            if (!mPowerManager.isScreenOn()) {//熄屏时
                Log.d(TAG, "wakeUpAndUnLock: wakeup");
                mWakeLock.acquire();
                mWakeLock.release();
            }
            Log.d(TAG, "wakeUpAndUnLock: isKeyguardLocked=" + mKeyguardManager.isKeyguardLocked() + " isKeyguardSecure=" + mKeyguardManager.isKeyguardSecure());
            if (mKeyguardManager.isKeyguardLocked() && !mKeyguardManager.isKeyguardSecure()) {
                mKeyguardLock.disableKeyguard();
                Log.d(TAG, "wakeUpAndUnLock: disableKeyguard");
            }
        } else {
            if (!mKeyguardManager.isKeyguardLocked()) {
                mKeyguardLock.reenableKeyguard();
                Log.d(TAG, "wakeUpAndUnLock: reenableKeyguard");
            }
        }

    }

    /**
     * 打开红包
     */

    private void openWeiXinPacket() {
        Log.d(TAG, "openWeiXinPacket: 开始打开红包");
        AccessibilityNodeInfo rootNodeInfo = getRootInActiveWindow();
        if (rootNodeInfo == null) {
            return;
        }

        AccessibilityNodeInfo openTarget = null;
        //可以根据id 也可以根据文字来操作
        //com.tencent.mm:id/bg_  看看大家的手气
        openTarget = findNodeInfosByText(rootNodeInfo, "看看大家的手气");
        Log.d(TAG, "openWeiXinPacket: openTarget=" + openTarget);
        if (openTarget != null) {//红包抢完了
            performGlobalAction(GLOBAL_ACTION_BACK);
        }
        if (openTarget == null) {
            for (int i = 0; i < rootNodeInfo.getChildCount(); i++) {
                AccessibilityNodeInfo note = rootNodeInfo.getChild(i);
                //可以根据id 也可以根据是否是Button.这些信息可以通过DDMS 来获取
                //android.widget.Button com.tencent.mm:id/bg7
                if ("android.widget.Button".equals(note.getClassName())) {
                    openTarget = note;
                    break;
                }
            }
        }

        //如果有"开"这个按钮图标就点击
        if (openTarget != null) {
            performClick(openTarget);
        }
    }

    /**
     * 准备打开红包:打开红包弹窗
     */
    @SuppressLint("NewApi")
    private void getWeiXinPacket() {
        Log.d(TAG, "getWeiXinPacket");
        AccessibilityNodeInfo rootNodeInfo = getRootInActiveWindow();
        if (rootNodeInfo == null) {
            return;
        }
        List<AccessibilityNodeInfo> nodes = rootNodeInfo.findAccessibilityNodeInfosByText("领取红包");
        Log.d(TAG, "getWeiXinPacket nodes is null? " + (nodes == null));
        if (nodes != null) {
            if (nodes.isEmpty()) {//在好友会话列表页面
                AccessibilityNodeInfo info = findNodeInfosByText(rootNodeInfo, "[微信红包]");
                if (info != null) {
                    canGetMonkey = true;
                    performClick(info);
                    Log.d(TAG, "getWeiXinPacket canGetMonkey= true");
                }
            } else {//在单个群会话页面
                if (canGetMonkey) {
                    //点击最新的红包
                    AccessibilityNodeInfo newMoney = nodes.get(nodes.size() - 1);
                    performClick(newMoney);
                    Log.d(TAG, "getWeiXinPacket canGetMonkey= false");
                    canGetMonkey = false;
                } else {//单个会话里面接到红包时
                    //单个会话:ListView
                    Log.d(TAG, "getWeiXinPacket 对话中有红包项");
                    //单个会话列表:ListView
                    /*AccessibilityNodeInfo messages = findNodeInfosById(rootNodeInfo,"com.tencent.mm:id/a25");
                    if(null != messages){
                        AccessibilityNodeInfo monkeyNode = null;
                        AccessibilityNodeInfo monkeyopendMessNote = null;
                        AccessibilityNodeInfo mextNote = null;

                        isGetMonkey = true;
                        int messageCount = messages.getChildCount();
                        Log.d(TAG, "getWeiXinPacket: messageCount=" + messageCount);
                        for (int i=0; i<messageCount;i++) {
                            monkeyNode = messages.getChild(i);
                            if(null != findNodeInfosByText(monkeyNode,"领取红包")){
                                monkeyopendMessNote = i==messageCount-1 ?null:messages.getChild(i+1);
                                mextNote = findNodeInfosByText(monkeyopendMessNote,"领取红包");//下一个记录还是红包项时,不领取,只领最新的那个
                                //红包项下一项不是"你领取xx的红包"就表示当前红包没有领取
                                monkeyopendMessNote = findNodeInfosById(monkeyopendMessNote,"com.tencent.mm:id/a3g");//头像
                                Log.d(TAG, "getWeiXinPacket: monkeyopendMessNote=" +monkeyopendMessNote +
                                        " \n mextNote=" +mextNote);
                                //当前记录的下一个记录不是"已领取的消息" 也不是红包消息
                                if ( monkeyopendMessNote == null && mextNote == null) {
                                    Log.d(TAG, "getWeiXinPacket: 有未领取的红包");
                                    performClick(findNodeInfosByText(monkeyNode,"领取红包"));
                                }else{
                                    Log.d(TAG, "getWeiXinPacket: 红包已经领取了或者不是最新的红包");
                                }
                            }
                        }


                        isGetMonkey = false;
                    }*/
                    //保存最新的红包,在单个会话页面,只判断最新的红包是否可以抢(是否已经抢过了)
                    isGetMonkey = true;
                    //单个会话列表:ListView
                    //AccessibilityNodeInfo messages = findNodeInfosById(rootNodeInfo,"com.tencent.mm:id/a25");
                    //mLastMonkeyNode = nodes.get(nodes.size()-1);
                    //int index = nodes.indexOf(mLastMonkeyNode);
                    for (int i = 0; i < nodes.size(); i++) {
                        Log.i(TAG, "getWeiXinPacket: i=" + i + " " + nodes.get(i));
                    }
                    isGetMonkey = false;
                }
            }
        }
    }

    /**
     * 点击指定的节点(不可以点击时点击其父节点)
     *
     * @param info
     */
    private void performClick(AccessibilityNodeInfo info) {
        Log.d(TAG, "performClick");
        if (info != null) {
            if (info.isClickable()) {
                info.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            } else {
                performClick(info.getParent());
            }
        }
    }

    /**
     * 模拟点击back 键返回一次
     */
    private void performBackClick() {
        Log.i(TAG, "performBackClick: ");
        performGlobalAction(GLOBAL_ACTION_BACK);
    }

    /**
     * 通过指定的文本查找节点
     *
     * @param rootNodeInfo
     * @param s
     * @return
     */
    private AccessibilityNodeInfo findNodeInfosByText(AccessibilityNodeInfo rootNodeInfo, String s) {
        //Log.d(TAG, "findNodeInfosByText() called with: s = [" + s + "]");
        if (rootNodeInfo != null) {
            List<AccessibilityNodeInfo> list = rootNodeInfo.findAccessibilityNodeInfosByText(s);
            if (list != null && !list.isEmpty()) {
                return list.get(0);
            }

        }
        return null;
    }

    /**
     * 通过指定的id查找节点
     *
     * @param rootNodeInfo
     * @param id
     * @return
     */
    private AccessibilityNodeInfo findNodeInfosById(AccessibilityNodeInfo rootNodeInfo, String id) {
        Log.d(TAG, "findNodeInfosById() called with: id = [" + id + "]");
        if (rootNodeInfo != null) {
            List<AccessibilityNodeInfo> list = rootNodeInfo.findAccessibilityNodeInfosByViewId(id);
            if (list != null && !list.isEmpty()) {
                return list.get(0);
            }

        }
        return null;
    }

    @Override
    public void onInterrupt() {
        Log.i(TAG, "onInterrupt: ");
    }


    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        Log.d(TAG, "onServiceConnected: 抢红包服务以及开启");
        mPowerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        mWakeLock = mPowerManager.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "rob_monkey_wakeup");
        mKeyguardManager = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        mKeyguardLock = mKeyguardManager.newKeyguardLock("rob_monkey_keyguard_lock");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "onDestroy: ");
        if (null != mWakeLock) {
            mWakeLock.release();
            mWakeLock = null;
        }
    }
}
