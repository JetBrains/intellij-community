package org.jetbrains.debugger.memory.utils;

import java.awt.event.KeyEvent;

public class KeyboardUtils {
  public static boolean isEnterKey(int keyCode) {
    return keyCode == KeyEvent.VK_ENTER;
  }

  public static boolean isUpDownKey(int keyCode) {
    return keyCode == KeyEvent.VK_UP || keyCode == KeyEvent.VK_DOWN;
  }

  public static boolean isBackSpace(int keyCode) {
    return keyCode == KeyEvent.VK_BACK_SPACE;
  }

  public static boolean isCharacter(int keyCode) {
    return KeyEvent.getKeyText(keyCode).length() == 1;
  }
}
