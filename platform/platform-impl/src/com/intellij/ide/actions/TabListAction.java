// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.ui.ExperimentalUI;
import com.intellij.ui.tabs.impl.MorePopupAware;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Shows the popup of all tabs when single row editor tab layout is used and all tabs don't fit on the screen.
 */
public class TabListAction extends DumbAwareAction {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    MorePopupAware morePopupAware = getMorePopupAware(e);
    if (morePopupAware != null) {
      morePopupAware.showMorePopup();
    }
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setIcon(ExperimentalUI.isNewUI() ? AllIcons.Toolbar.Expand : AllIcons.Actions.FindAndShowNextMatches);
    if (ApplicationManager.getApplication().isHeadlessEnvironment()) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }
    e.getPresentation().setEnabledAndVisible(isTabListAvailable(e));
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  private static boolean isTabListAvailable(@NotNull AnActionEvent e) {
    MorePopupAware morePopupAware = getMorePopupAware(e);
    return morePopupAware != null && morePopupAware.canShowMorePopup();
  }

  private static @Nullable MorePopupAware getMorePopupAware(@NotNull AnActionEvent e) {
    return e.getPlace().equals(ActionPlaces.TOOLWINDOW_TITLE)
      ? e.getData(MorePopupAware.KEY_TOOLWINDOW_TITLE)
      : e.getData(MorePopupAware.KEY);
  }
}
