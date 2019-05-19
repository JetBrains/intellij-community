// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.ui.tabs.JBTabsEx;
import org.jetbrains.annotations.NotNull;

/**
 * Shows the popup of all tabs when single row editor tab layout is used and all tabs don't fit on the screen.
 *
 * @author yole
 */
public class TabListAction extends AnAction {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    JBTabsEx tabs = e.getData(JBTabsEx.NAVIGATION_ACTIONS_KEY);
    if (tabs != null) {
      tabs.showMorePopup(null);
    }
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabled(isTabListAvailable(e));
  }

  private static boolean isTabListAvailable(@NotNull AnActionEvent e) {
    JBTabsEx tabs = e.getData(JBTabsEx.NAVIGATION_ACTIONS_KEY);
    if (tabs == null || !tabs.isEditorTabs()) {
      return false;
    }
    return tabs.canShowMorePopup();
  }
}
