/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;

public class CommonShortcuts {

  private CommonShortcuts() {}

  public static final ShortcutSet ALT_ENTER = new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, KeyEvent.ALT_DOWN_MASK));
  public static final ShortcutSet ENTER = new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0));
  public static final ShortcutSet CTRL_ENTER = new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER,
                                                                                            SystemInfo.isMac
                                                                                            ? KeyEvent.META_DOWN_MASK
                                                                                            : KeyEvent.CTRL_DOWN_MASK));
  public static final ShortcutSet INSERT = new CustomShortcutSet(SystemInfo.isMac ? KeyStroke.getKeyStroke(KeyEvent.VK_N, KeyEvent.CTRL_DOWN_MASK) 
                                                                                  : KeyStroke.getKeyStroke(KeyEvent.VK_INSERT, 0));
  public static final ShortcutSet DELETE = new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0));
  public static final ShortcutSet ESCAPE = new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0));

  public static final ShortcutSet DOUBLE_CLICK_1 = new CustomShortcutSet(new Shortcut[]{new MouseShortcut(MouseEvent.BUTTON1, 0, 2)});

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
    return new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_D, SystemInfo.isMac ? KeyEvent.META_DOWN_MASK : KeyEvent.CTRL_DOWN_MASK));
  }

  public static ShortcutSet getFind() {
    return new CustomShortcutSet(KeymapManager.getInstance().getActiveKeymap().getShortcuts(IdeActions.ACTION_FIND));
  }
}
