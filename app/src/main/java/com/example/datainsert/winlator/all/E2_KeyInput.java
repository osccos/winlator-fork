package com.example.datainsert.winlator.all;

import static com.winlator.xserver.Keyboard.KEYSYMS_PER_KEYCODE;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;

import com.winlator.xserver.Bitmask;
import com.winlator.xserver.Window;
import com.winlator.xserver.XKeycode;
import com.winlator.xserver.XLock;
import com.winlator.xserver.XServer;
import com.winlator.xserver.events.Event;
import com.winlator.xserver.events.MappingNotify;

public class E2_KeyInput {
    private static final String TAG = "E2_KeyInput";
    /** 用于输入unicode文字时，临时充数的keycode */
    public static final XKeycode[] avaiKeyCode = {XKeycode.KEY_A, XKeycode.KEY_B, XKeycode.KEY_C, XKeycode.KEY_D, XKeycode.KEY_E, XKeycode.KEY_F, XKeycode.KEY_G, XKeycode.KEY_H, XKeycode.KEY_I, XKeycode.KEY_J, XKeycode.KEY_K, XKeycode.KEY_L, XKeycode.KEY_M, XKeycode.KEY_N, XKeycode.KEY_O, XKeycode.KEY_P, XKeycode.KEY_Q, XKeycode.KEY_R, XKeycode.KEY_S, XKeycode.KEY_T, XKeycode.KEY_U, XKeycode.KEY_V, XKeycode.KEY_W, XKeycode.KEY_X, XKeycode.KEY_Y, XKeycode.KEY_Z,};
    /** 用于输入unicode文字时，记录本次该用哪个充数的keycode，然后++ */
    public static int  currIndex = 0;



    /**
     * 此函数只处理KeyEvent.ACTION_MULTIPLE的情况
     */
    public static boolean handleAndroidKeyEvent(XServer xServer, KeyEvent event){

        boolean handled = false;
        if(event.getAction() == KeyEvent.ACTION_MULTIPLE){
            String characters = event.getCharacters();
            for (int i = 0; i < characters.codePointCount(0, characters.length()); i++) {
                int keycode = avaiKeyCode[currIndex].id;
                int keySym = characters.codePointAt(characters.offsetByCodePoints(0, i));
                //大于0xff的，直接加上0x100,0000
                if(keySym>0xff) keySym = keySym | 0x1000000;

                int[] oriKeySyms = getCurrentKeysym(xServer, keycode);

                xServer.injectKeyPress(avaiKeyCode[currIndex], keySym);
                xServer.injectKeyRelease(avaiKeyCode[currIndex]);

                //手动把keycode对应的unicode keysym改回来。直接Thread.sleep还不行，只能postDelay了
                int[] nowKeySyms = getCurrentKeysym(xServer, keycode);
                if(oriKeySyms[0] != nowKeySyms[0] || oriKeySyms[1] != nowKeySyms[1])
                    new Handler(Looper.getMainLooper()).postDelayed(()-> mappingKeySymBackToOrigin(xServer, (byte) keycode, oriKeySyms), 30);

                currIndex = (currIndex+1)%avaiKeyCode.length;//数组下标+1，为下一次设置另一个keycode做准备
                handled = true;
            }
            Log.d(TAG, "handleAKeyEvent: action=multiple, string="+characters);
        }
        return handled;
    }

    private static int[] getCurrentKeysym(XServer xServer, int keycode){
        int keysymIdxStart = (keycode-8)*KEYSYMS_PER_KEYCODE;
        int[] keyboardKeysyms = xServer.keyboard.keysyms;
        return new int[]{keyboardKeysyms[keysymIdxStart],keyboardKeysyms[keysymIdxStart+1]};
    }

    /**
     * injectKeyPress最终会走到InputDeviceManager.onKeyPress，
     * 如果xServer.keyboard所存的keycode对应的keysym不是传入的，且keysym不为0，则将其设置为此keycode的keysym，并发送MappingNotify
     * 然后后续没有恢复原先keycode的操作。所以这里要手动模拟一下咯
     * @param keycode 输入unicode按下事件时对应的keycode
     * @param oriKeySyms keycode原先的两个keysym
     */
    private static void mappingKeySymBackToOrigin(XServer xServer, byte keycode, int[] oriKeySyms){
        try (XLock lock = xServer.lock(XServer.Lockable.WINDOW_MANAGER, XServer.Lockable.INPUT_DEVICE)) {
            Window focusedWindow = xServer.windowManager.getFocusedWindow();
            if (focusedWindow == null) return;
            Window pointWindow = xServer.windowManager.findPointWindow(xServer);

            Window eventWindow;
            if (focusedWindow.isAncestorOf(pointWindow)) {
                eventWindow = pointWindow.getAncestorWithEventId(Event.KEY_PRESS, focusedWindow);
            }else if (focusedWindow.hasEventListenerFor(Event.KEY_PRESS))
                eventWindow = focusedWindow;
            else return;

            if (!eventWindow.attributes.isEnabled()) return;

            xServer.keyboard.setKeysyms(keycode, oriKeySyms[0], oriKeySyms[1]);
            eventWindow.sendEvent(new MappingNotify(MappingNotify.Request.KEYBOARD, keycode, 1));
        }
    }
}
