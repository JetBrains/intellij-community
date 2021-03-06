// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.jcef;

import org.cef.handler.CefKeyboardHandler.CefKeyEvent;
import org.cef.misc.EventFlags;

import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.HashMap;
import java.util.Map;

/**
 * @author tav
 */
final class JBCefEventUtils {
  private static final Map<Integer, Integer> CEF_2_JAVA_KEYCODES = new HashMap<>();
  private static final Map<Integer, Integer> CEF_2_JAVA_MODIFIERS = new HashMap<>();

  static {
    // CEF uses Windows VK: https://github.com/adobe/webkit/blob/master/Source/WebCore/platform/chromium/KeyboardCodes.h
    // [tav] todo: check if there's more VK's to manually convert
    CEF_2_JAVA_KEYCODES.put(0x0d, KeyEvent.VK_ENTER);
    CEF_2_JAVA_KEYCODES.put(0x08, KeyEvent.VK_BACK_SPACE);
    CEF_2_JAVA_KEYCODES.put(0x09, KeyEvent.VK_TAB);

    CEF_2_JAVA_MODIFIERS.put(EventFlags.EVENTFLAG_CONTROL_DOWN, InputEvent.CTRL_DOWN_MASK);
    CEF_2_JAVA_MODIFIERS.put(EventFlags.EVENTFLAG_SHIFT_DOWN, InputEvent.SHIFT_DOWN_MASK);
    CEF_2_JAVA_MODIFIERS.put(EventFlags.EVENTFLAG_ALT_DOWN, InputEvent.ALT_DOWN_MASK);
    CEF_2_JAVA_MODIFIERS.put(EventFlags.EVENTFLAG_LEFT_MOUSE_BUTTON, InputEvent.BUTTON1_DOWN_MASK);
    CEF_2_JAVA_MODIFIERS.put(EventFlags.EVENTFLAG_MIDDLE_MOUSE_BUTTON, InputEvent.BUTTON2_DOWN_MASK);
    CEF_2_JAVA_MODIFIERS.put(EventFlags.EVENTFLAG_RIGHT_MOUSE_BUTTON, InputEvent.BUTTON3_DOWN_MASK);
  }

  public static KeyEvent convertCefKeyEvent(CefKeyEvent cefKeyEvent, Component source) {
    //noinspection MagicConstant
    return new KeyEvent(source,
                        convertCefKeyEventType(cefKeyEvent),
                        System.currentTimeMillis(),
                        convertCefKeyEventModifiers(cefKeyEvent),
                        convertCefKeyEventKeyCode(cefKeyEvent),
                        cefKeyEvent.character,
                        KeyEvent.KEY_LOCATION_UNKNOWN);
  }

  public static KeyEvent javaKeyEventWithID(KeyEvent javaKeyEvent, int id) {
    return new KeyEvent(javaKeyEvent.getComponent(),
                        id,
                        javaKeyEvent.getWhen(),
                        javaKeyEvent.getModifiers(),
                        javaKeyEvent.getKeyCode(),
                        javaKeyEvent.getKeyChar(),
                        javaKeyEvent.getKeyLocation());
  }

  public static int convertCefKeyEventType(CefKeyEvent cefKeyEvent) {
    switch (cefKeyEvent.type) {
      case KEYEVENT_RAWKEYDOWN:
      case KEYEVENT_KEYDOWN:
        return KeyEvent.KEY_PRESSED;
      case KEYEVENT_KEYUP:
        return KeyEvent.KEY_RELEASED;
      case KEYEVENT_CHAR:
        return KeyEvent.KEY_TYPED;
      default:
        assert false;
        return -1; // not reachable
    }
  }

  public static int convertCefKeyEventKeyCode(CefKeyEvent cefKeyEvent) {
    Integer value = CEF_2_JAVA_KEYCODES.get(cefKeyEvent.windows_key_code);
    if (value != null) return value;
    return cefKeyEvent.windows_key_code;
  }

  public static int convertCefKeyEventModifiers(CefKeyEvent cefKeyEvent) {
    int javaModifiers = 0;

    for (Map.Entry<Integer, Integer> entry : CEF_2_JAVA_MODIFIERS.entrySet()) {
      if ((cefKeyEvent.modifiers & entry.getKey()) != 0) {
        javaModifiers |= entry.getValue();
      }
    }
    return javaModifiers;
  }

  public static boolean isUpDownKeyEvent(CefKeyEvent cefKeyEvent) {
    return cefKeyEvent.windows_key_code == KeyEvent.VK_UP || cefKeyEvent.windows_key_code == KeyEvent.VK_DOWN;
  }
}
