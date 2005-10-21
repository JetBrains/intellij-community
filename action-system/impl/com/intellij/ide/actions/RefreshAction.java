/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.*;

import javax.swing.*;

/**
 * This class is here just to be able to assign shortcut to all "refresh"  actions from the keymap.
 * It also serves as a base action for 'refresh' actions (to make dependencies more clear) and
 * provides a convenience method to register its shortcut on a component
 */
public class RefreshAction extends AnAction{
  public RefreshAction() {
  }

  public RefreshAction(String text) {
    super(text);
  }

  public RefreshAction(String text, String description, Icon icon) {
    super(text, description, icon);
  }

  public void actionPerformed(AnActionEvent e) {
    // empty
  }

  public void registerShortcutOn(JComponent component) {
    final ShortcutSet shortcutSet = ActionManager.getInstance().getAction(IdeActions.ACTION_REFRESH).getShortcutSet();
    registerCustomShortcutSet(shortcutSet, component);
  }
}
