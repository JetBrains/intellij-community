/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.actionSystem.MouseShortcut;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.registry.RegistryValue;
import com.intellij.openapi.util.registry.RegistryValueListener;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import java.util.StringTokenizer;

public class KeymapUtil {
  private static final Icon ourKeyboardShortcutIcon = IconLoader.getIcon("/general/keyboardShortcut.png");
  private static final Icon ourMouseShortcutIcon = IconLoader.getIcon("/general/mouseShortcut.png");
  @NonNls private static final String APPLE_LAF_AQUA_LOOK_AND_FEEL_CLASS_NAME = "apple.laf.AquaLookAndFeel";
  @NonNls private static final String GET_KEY_MODIFIERS_TEXT_METHOD = "getKeyModifiersText";
  @NonNls private static final String CANCEL_KEY_TEXT = "Cancel";
  @NonNls private static final String BREAK_KEY_TEXT = "Break";
  @NonNls private static final String SHIFT = "shift";
  @NonNls private static final String CONTROL = "control";
  @NonNls private static final String CTRL = "ctrl";
  @NonNls private static final String META = "meta";
  @NonNls private static final String ALT = "alt";
  @NonNls private static final String ALT_GRAPH = "altGraph";
  @NonNls private static final String BUTTON1 = "button1";
  @NonNls private static final String BUTTON2 = "button2";
  @NonNls private static final String BUTTON3 = "button3";
  @NonNls private static final String DOUBLE_CLICK = "doubleClick";

  private static final Set<Integer> ourTooltipKeys = new HashSet<Integer>();
  private static final Set<Integer> ourOtherTooltipKeys = new HashSet<Integer>();
  private static RegistryValue ourTooltipKeysProperty;

  public static String getShortcutText(Shortcut shortcut) {
    String s = "";

    if (shortcut instanceof KeyboardShortcut) {
      KeyboardShortcut keyboardShortcut = (KeyboardShortcut)shortcut;

      String acceleratorText = getKeystrokeText(keyboardShortcut.getFirstKeyStroke());
      if (acceleratorText.length() > 0) {
        s = acceleratorText;
      }

      acceleratorText = getKeystrokeText(keyboardShortcut.getSecondKeyStroke());
      if (acceleratorText.length() > 0) {
        s += ", " + acceleratorText;
      }
    }
    else if (shortcut instanceof MouseShortcut) {
      MouseShortcut mouseShortcut = (MouseShortcut)shortcut;
      s = getMouseShortcutText(mouseShortcut.getButton(), mouseShortcut.getModifiers(), mouseShortcut.getClickCount());
    }
    else {
      throw new IllegalArgumentException("unknown shortcut class: " + shortcut);
    }
    return s;
  }

  public static Icon getShortcutIcon(Shortcut shortcut) {
    if (shortcut instanceof KeyboardShortcut) {
      return ourKeyboardShortcutIcon;
    }
    else if (shortcut instanceof MouseShortcut) {
      return ourMouseShortcutIcon;
    }
    else {
      throw new IllegalArgumentException("unknown shortcut class: " + shortcut);
    }
  }

  /**
   * @return string representation of passed mouse shortcut.
   */
  public static String getMouseShortcutText(int button, int modifiers, int clickCount) {
    // Modal keys

    final int buttonNum;

    if (MouseEvent.BUTTON1 == button) {
      buttonNum = 1;
    }
    else if (MouseEvent.BUTTON2 == button) {
      buttonNum = 2;
    }
    else if (MouseEvent.BUTTON3 == button) {
      buttonNum = 3;
    }
    else if (MouseEvent.NOBUTTON == button || -1 == button) {
      buttonNum = 0;
      // do nothing
    }
    else {
      throw new IllegalStateException("unknown button: " + button);
    }

    if (clickCount == 1) {
      return KeyMapBundle.message("mouse.click.shortcut.text", getModifiersText(mapNewModifiers(modifiers)), buttonNum);
    }
    else if (clickCount == 2) {
      return KeyMapBundle.message("mouse.double.click.shortcut.text", getModifiersText(mapNewModifiers(modifiers)), buttonNum);
    }
    else {
      throw new IllegalStateException("unknown clickCount: " + clickCount);
    }
  }

  private static int mapNewModifiers(int modifiers) {
    if ((modifiers & InputEvent.SHIFT_DOWN_MASK) != 0) {
      modifiers |= InputEvent.SHIFT_MASK;
    }
    if ((modifiers & InputEvent.ALT_DOWN_MASK) != 0) {
      modifiers |= InputEvent.ALT_MASK;
    }
    if ((modifiers & InputEvent.ALT_GRAPH_DOWN_MASK) != 0) {
      modifiers |= InputEvent.ALT_GRAPH_MASK;
    }
    if ((modifiers & InputEvent.CTRL_DOWN_MASK) != 0) {
      modifiers |= InputEvent.CTRL_MASK;
    }
    if ((modifiers & InputEvent.META_DOWN_MASK) != 0) {
      modifiers |= InputEvent.META_MASK;
    }

    return modifiers;
  }

  public static String getKeystrokeText(KeyStroke accelerator) {
    if (accelerator == null) return "";
    String acceleratorText = "";
    int modifiers = accelerator.getModifiers();
    if (modifiers > 0) {
      acceleratorText = getModifiersText(modifiers);
    }

    String keyText = KeyEvent.getKeyText(accelerator.getKeyCode());
    // [vova] this is dirty fix for bug #35092 
    if(CANCEL_KEY_TEXT.equals(keyText)){
      keyText = BREAK_KEY_TEXT;
    }

    acceleratorText += keyText;
    return acceleratorText.trim();
  }

  private static String getModifiersText(int modifiers) {
    if (SystemInfo.isMac) {
      try {
        Class appleLaf = Class.forName(APPLE_LAF_AQUA_LOOK_AND_FEEL_CLASS_NAME);
        Method getModifiers = appleLaf.getMethod(GET_KEY_MODIFIERS_TEXT_METHOD, new Class[]{int.class, boolean.class});
        return (String)getModifiers.invoke(appleLaf, new Object[]{new Integer(modifiers), Boolean.FALSE});
      }
      catch (Exception e) {
        if (SystemInfo.isMacOSLeopard) {
          return KeymapUtil.getKeyModifiersTextForMacOSLeopard(modifiers);
        }
        
        // OK do nothing here.
      }
    }

    final String keyModifiersText = KeyEvent.getKeyModifiersText(modifiers);
    if (keyModifiersText.length() > 0) {
      return keyModifiersText + "+";
    } else {
      return keyModifiersText;
    }
  }

  public static String getFirstKeyboardShortcutText(AnAction action) {
    Shortcut[] shortcuts = action.getShortcutSet().getShortcuts();
    for (int i = 0; i < shortcuts.length; i++) {
      Shortcut shortcut = shortcuts[i];
      if (shortcut instanceof KeyboardShortcut) {
        return getShortcutText(shortcut);
      }
    }
    return "";
  }

  public static String getShortcutsText(Shortcut[] shortcuts) {
    if (shortcuts.length == 0) {
      return "";
    }
    StringBuffer buffer = new StringBuffer();
    for (int i = 0; i < shortcuts.length; i++) {
      Shortcut shortcut = shortcuts[i];
      if (i > 0) {
        buffer.append(' ');
      }
      buffer.append(KeymapUtil.getShortcutText(shortcut));
    }
    return buffer.toString();
  }

  /**
   * Factory method. It parses passed string and creates <code>MouseShortcut</code>.
   * 
   * @throws InvalidDataException if <code>keystrokeString</code> doesn't represent valid <code>MouseShortcut</code>.
   */
  public static MouseShortcut parseMouseShortcut(String keystrokeString) throws InvalidDataException {
    int button = -1;
    int modifiers = 0;
    int clickCount = 1;
    for (StringTokenizer tokenizer = new StringTokenizer(keystrokeString); tokenizer.hasMoreTokens();) {
      String token = tokenizer.nextToken();
      if (SHIFT.equals(token)) {
        modifiers |= MouseEvent.SHIFT_DOWN_MASK;
      }
      else if (CONTROL.equals(token) || CTRL.equals(token)) {
        modifiers |= MouseEvent.CTRL_DOWN_MASK;
      }
      else if (META.equals(token)) {
        modifiers |= MouseEvent.META_DOWN_MASK;
      }
      else if (ALT.equals(token)) {
        modifiers |= MouseEvent.ALT_DOWN_MASK;
      }
      else if (ALT_GRAPH.equals(token)) {
        modifiers |= MouseEvent.ALT_GRAPH_DOWN_MASK;
      }
      else if (BUTTON1.equals(token)) {
        button = MouseEvent.BUTTON1;
      }
      else if (BUTTON2.equals(token)) {
        button = MouseEvent.BUTTON2;
      }
      else if (BUTTON3.equals(token)) {
        button = MouseEvent.BUTTON3;
      }
      else if (DOUBLE_CLICK.equals(token)) {
        clickCount = 2;
      }
      else {
        throw new InvalidDataException("unknown token: " + token);
      }
    }
    return new MouseShortcut(button, modifiers, clickCount);
  }

  public static String getKeyModifiersTextForMacOSLeopard(int modifiers) {
      StringBuffer buf = new StringBuffer();
      if ((modifiers & InputEvent.META_MASK) != 0) {
          buf.append(Toolkit.getProperty("AWT.meta", "Meta"));
      }
      if ((modifiers & InputEvent.CTRL_MASK) != 0) {
          buf.append(Toolkit.getProperty("AWT.control", "Ctrl"));
      }
      if ((modifiers & InputEvent.ALT_MASK) != 0) {
          buf.append(Toolkit.getProperty("AWT.alt", "Alt"));
      }
      if ((modifiers & InputEvent.SHIFT_MASK) != 0) {
          buf.append(Toolkit.getProperty("AWT.shift", "Shift"));
      }
      if ((modifiers & InputEvent.ALT_GRAPH_MASK) != 0) {
          buf.append(Toolkit.getProperty("AWT.altGraph", "Alt Graph"));
      }
      if ((modifiers & InputEvent.BUTTON1_MASK) != 0) {
          buf.append(Toolkit.getProperty("AWT.button1", "Button1"));
      }
      return buf.toString();
  }

  public static boolean isTooltipRequest(KeyEvent keyEvent) {
    if (ourTooltipKeysProperty == null) {
      ourTooltipKeysProperty = Registry.get("ide.forcedShowTooltip");
      ourTooltipKeysProperty.addListener(new RegistryValueListener.Adapter() {
        @Override
        public void afterValueChanged(RegistryValue value) {
          updateTooltipRequestKey(value);
        }
      }, Disposer.get("ui"));

      updateTooltipRequestKey(ourTooltipKeysProperty);
    }

    if (keyEvent.getID() != KeyEvent.KEY_PRESSED) return false;

    for (Integer each : ourTooltipKeys) {
      if ((keyEvent.getModifiers() & each.intValue()) == 0) return false;
    }

    for (Integer each : ourOtherTooltipKeys) {
      if ((keyEvent.getModifiers() & each.intValue()) > 0) return false;
    }

    final int code = keyEvent.getKeyCode();

    return code == KeyEvent.VK_META || code == KeyEvent.VK_CONTROL || code == KeyEvent.VK_SHIFT | code == KeyEvent.VK_ALT;
  }

  private static void updateTooltipRequestKey(RegistryValue value) {
    final String text = value.asString();

    ourTooltipKeys.clear();
    ourOtherTooltipKeys.clear();

    processKey(text.contains("meta"), KeyEvent.META_MASK);
    processKey(text.contains("control") | text.contains("ctrl"), KeyEvent.CTRL_MASK);
    processKey(text.contains("shift"), KeyEvent.SHIFT_MASK);
    processKey(text.contains("alt"), KeyEvent.ALT_MASK);

  }

  private static void processKey(boolean condition, int value) {
    if (condition) {
      ourTooltipKeys.add(value);
    } else {
      ourOtherTooltipKeys.add(value);
    }
  }
}
