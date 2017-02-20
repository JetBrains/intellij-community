/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.debugger.memory.utils;

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
