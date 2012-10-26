/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.openapi.keymap;

import org.intellij.lang.annotations.JdkConstants;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

/**
 * Utility class to display action shortcuts in Mac menus
 *
 * @author Konstantin Bulenkov
 */
@SuppressWarnings("UnusedDeclaration")
public class MacKeymapUtil {
  public static final String Escape	= "\u238B"; //⎋
  public static final String Tab	= "\u21E5"; //⇥
  public static final String Tab_back	= "\u21E4"; //⇤
  public static final String Capslock	= "\u21EA"; //⇪
  public static final String Shift	= "\u21E7"; //⇧
  public static final String Control	= "\u2303"; //⌃
  public static final String Option     = "\u2325"; //⌥
  public static final String Apple      = "\uF8FF"; //
  public static final String Command    = "\u2318"; //⌘
  public static final String Space	= "\u2423"; //␣
  public static final String Return	= "\u23CE"; //⏎
  public static final String Backspace	= "\u232B"; //⌫
  public static final String Delete	= "\u2326"; //⌦
  public static final String Home       = "\u2196"; //↖
  public static final String End	= "\u2198"; //↘
  public static final String Pageup	= "\u21DE"; //⇞
  public static final String Pagedown	= "\u21DF"; //⇟
  public static final String Up	        = "\u2191"; //↑
  public static final String Down	= "\u2193"; //↓
  public static final String Left	= "\u2190"; //←
  public static final String Right	= "\u2192"; //→
  public static final String Clear	= "\u2327"; //⌧
  public static final String Numberlock	= "\u21ED"; //⇭
  public static final String Enter	= "\u2324"; //⌤
  public static final String Eject	= "\u23CF"; //⏏
  public static final String Power3	= "\u233D"; //⌽
  public static final String NUMPAD     = "\u2328"; //⌨

  public static String getModifiersText(@JdkConstants.InputEventMask int modifiers) {
    StringBuilder buf = new StringBuilder();
    if ((modifiers & InputEvent.CTRL_MASK) != 0) buf.append(Control);
    if ((modifiers & InputEvent.ALT_MASK) != 0) buf.append(Option);
    if ((modifiers & InputEvent.SHIFT_MASK) != 0) buf.append(Shift);
    if ((modifiers & InputEvent.ALT_GRAPH_MASK) != 0) buf.append(Toolkit.getProperty("AWT.altGraph", "Alt Graph"));
    if ((modifiers & InputEvent.BUTTON1_MASK) != 0) buf.append(Toolkit.getProperty("AWT.button1", "Button1"));
    if ((modifiers & InputEvent.META_MASK) != 0) buf.append(Command);
    return buf.toString();

  }

  public static String getKeyText(int code) {
    switch (code) {
      case KeyEvent.VK_BACK_SPACE:     return Backspace;
      case KeyEvent.VK_ESCAPE:         return Escape;
      case KeyEvent.VK_CAPS_LOCK:      return Capslock;
      case KeyEvent.VK_TAB:            return Tab;
      case KeyEvent.VK_SPACE:          return Space;
      case KeyEvent.VK_DELETE:         return Delete;
      case KeyEvent.VK_HOME:           return Home;
      case KeyEvent.VK_END:            return End;
      case KeyEvent.VK_PAGE_UP:        return Pageup;
      case KeyEvent.VK_PAGE_DOWN:      return Pagedown;
      case KeyEvent.VK_UP:             return Up;
      case KeyEvent.VK_DOWN:           return Down;
      case KeyEvent.VK_LEFT:           return Left;
      case KeyEvent.VK_RIGHT:          return Right;
      case KeyEvent.VK_NUM_LOCK:       return Numberlock;
      case KeyEvent.VK_ENTER:          return Return;
      case KeyEvent.VK_BACK_QUOTE:     return "`";
      case KeyEvent.VK_NUMBER_SIGN:    return NUMPAD;
      case KeyEvent.VK_MULTIPLY:       return NUMPAD + " *";
      case KeyEvent.VK_ADD:            return "+";
      case KeyEvent.VK_SEPARATOR:      return ",";
      case KeyEvent.VK_SUBTRACT:       return "-";
      case KeyEvent.VK_DECIMAL:        return ".";
      case KeyEvent.VK_DIVIDE:         return "/";
      case KeyEvent.VK_NUMPAD0:        return "0";
      case KeyEvent.VK_NUMPAD1:        return "1";
      case KeyEvent.VK_NUMPAD2:        return "2";
      case KeyEvent.VK_NUMPAD3:        return "3";
      case KeyEvent.VK_NUMPAD4:        return "4";
      case KeyEvent.VK_NUMPAD5:        return "5";
      case KeyEvent.VK_NUMPAD6:        return "6";
      case KeyEvent.VK_NUMPAD7:        return "7";
      case KeyEvent.VK_NUMPAD8:        return "8";
      case KeyEvent.VK_NUMPAD9:        return "9";
      case KeyEvent.VK_SLASH:          return "/";
      case KeyEvent.VK_BACK_SLASH:     return "\\";
      case KeyEvent.VK_PERIOD:         return ".";
      case KeyEvent.VK_SEMICOLON:      return ";";
      case KeyEvent.VK_CLOSE_BRACKET:  return "]";
      case KeyEvent.VK_OPEN_BRACKET:   return "[";
      case KeyEvent.VK_EQUALS:         return "=";
      case KeyEvent.VK_MINUS:          return "-";
      case KeyEvent.VK_PLUS:           return "+";
      case 0:                          return "fn";
    }
    return KeyEvent.getKeyText(code);
  }

  public static String getKeyStrokeText(KeyStroke keyStroke) {
    final String modifiers = getModifiersText(keyStroke.getModifiers());
    final String key = getKeyText(keyStroke.getKeyCode());
    return modifiers + key;
  }
}
