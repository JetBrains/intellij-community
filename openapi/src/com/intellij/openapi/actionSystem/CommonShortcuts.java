/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
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
  public static final ShortcutSet INSERT = new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_INSERT, 0));
  public static final ShortcutSet DELETE = new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0));
  public static final ShortcutSet ESCAPE = new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0));

  public static final ShortcutSet DOUBLE_CLICK_1 = new CustomShortcutSet(new Shortcut[]{new MouseShortcut(MouseEvent.BUTTON1, 0, 2)});

  public static ShortcutSet getRerun() {
    return new CustomShortcutSet(KeymapManager.getInstance().getActiveKeymap().getShortcuts(IdeActions.ACTION_RERUN));
  }

  public static ShortcutSet getEditSource() {
    return new CustomShortcutSet(KeymapManager.getInstance().getActiveKeymap().getShortcuts(IdeActions.ACTION_EDIT_SOURCE));
  }
}
