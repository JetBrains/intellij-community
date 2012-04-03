/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.actionSystem;

import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.util.SystemInfo;

import javax.swing.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;

public class CommonShortcuts {

  private CommonShortcuts() {}

  public static final ShortcutSet ALT_ENTER = new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.ALT_DOWN_MASK));
  public static final ShortcutSet ENTER = new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0));
  public static final ShortcutSet CTRL_ENTER = new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER,
                                                                                            SystemInfo.isMac
                                                                                            ? InputEvent.META_DOWN_MASK
                                                                                            : InputEvent.CTRL_DOWN_MASK));
  public static final ShortcutSet INSERT = new CustomShortcutSet(getInsertKeystroke());
  public static final ShortcutSet DELETE = new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0));
  public static final ShortcutSet ESCAPE = new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0));

  public static final ShortcutSet DOUBLE_CLICK_1 = new CustomShortcutSet(new MouseShortcut(MouseEvent.BUTTON1, 0, 2));
  
  public static ShortcutSet getNewForDialogs() {
    final ArrayList<Shortcut> shortcuts = new ArrayList<Shortcut>();
    for (Shortcut shortcut : getNew().getShortcuts()) {
      if (isCtrlEnter(shortcut)) continue;
      shortcuts.add(shortcut);
    }
    return new CustomShortcutSet(shortcuts.toArray(new Shortcut[shortcuts.size()]));
  }

  private static boolean isCtrlEnter(Shortcut shortcut) {
    if (shortcut instanceof KeyboardShortcut) {
      KeyStroke keyStroke = ((KeyboardShortcut)shortcut).getFirstKeyStroke();
      return keyStroke != null
        && keyStroke.getKeyCode() == KeyEvent.VK_ENTER
        && (keyStroke.getModifiers() & InputEvent.CTRL_MASK) != 0;
    }
    return false;
  }

  public static KeyStroke getInsertKeystroke() {
    return SystemInfo.isMac ? KeyStroke.getKeyStroke(KeyEvent.VK_N, InputEvent.CTRL_DOWN_MASK)
                            : KeyStroke.getKeyStroke(KeyEvent.VK_INSERT, 0);
  }

  public static ShortcutSet getCopy() {
    return new CustomShortcutSet(KeymapManager.getInstance().getActiveKeymap().getShortcuts(IdeActions.ACTION_COPY));
  }

  public static ShortcutSet getRerun() {
    return new CustomShortcutSet(KeymapManager.getInstance().getActiveKeymap().getShortcuts(IdeActions.ACTION_RERUN));
  }

  public static ShortcutSet getEditSource() {
    return new CustomShortcutSet(KeymapManager.getInstance().getActiveKeymap().getShortcuts(IdeActions.ACTION_EDIT_SOURCE));
  }

  public static ShortcutSet getViewSource() {
    return new CustomShortcutSet(KeymapManager.getInstance().getActiveKeymap().getShortcuts(IdeActions.ACTION_VIEW_SOURCE));
  }

  public static ShortcutSet getNew() {
    return new CustomShortcutSet(KeymapManager.getInstance().getActiveKeymap().getShortcuts(IdeActions.ACTION_NEW_ELEMENT));
  }

  public static ShortcutSet getMove() {
    return new CustomShortcutSet(KeymapManager.getInstance().getActiveKeymap().getShortcuts(IdeActions.ACTION_MOVE));
  }


  public static ShortcutSet getRename() {
    return new CustomShortcutSet(KeymapManager.getInstance().getActiveKeymap().getShortcuts(IdeActions.ACTION_RENAME));
  }

  public static ShortcutSet getDiff() {
    return new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_D, SystemInfo.isMac ? InputEvent.META_DOWN_MASK
                                                                                        : InputEvent.CTRL_DOWN_MASK));
  }

  public static ShortcutSet getFind() {
    return new CustomShortcutSet(KeymapManager.getInstance().getActiveKeymap().getShortcuts(IdeActions.ACTION_FIND));
  }

  public static ShortcutSet getContextHelp() {
    return new CustomShortcutSet(KeymapManager.getInstance().getActiveKeymap().getShortcuts(IdeActions.ACTION_CONTEXT_HELP));
  }
}
