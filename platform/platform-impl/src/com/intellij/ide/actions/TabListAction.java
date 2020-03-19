// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.ui.tabs.impl.MorePopupAware;
import org.jetbrains.annotations.NotNull;

/**
 * Shows the popup of all tabs when single row editor tab layout is used and all tabs don't fit on the screen.
 *
 * @author yole
 */
public class TabListAction extends DumbAwareAction {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    MorePopupAware morePopupAware = e.getData(MorePopupAware.KEY);
    if (morePopupAware != null) {
      morePopupAware.showMorePopup();
    }
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setIcon(AllIcons.Actions.FindAndShowNextMatches);
    if (ApplicationManager.getApplication().isHeadlessEnvironment()) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }
    boolean available = isTabListAvailable(e) || e.getPlace() == ActionPlaces.TABS_MORE_TOOLBAR;
    e.getPresentation().setEnabledAndVisible(available);
  }

  private static boolean isTabListAvailable(@NotNull AnActionEvent e) {
    MorePopupAware morePopupAware = e.getData(MorePopupAware.KEY);
    return morePopupAware != null && morePopupAware.canShowMorePopup();
  }
}
