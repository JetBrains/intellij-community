// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.navigationToolbar;

import com.intellij.ide.navbar.ide.NavBarIdeUtil;
import com.intellij.ide.navbar.ide.NavBarService;
import com.intellij.ide.ui.NavBarLocation;
import com.intellij.ide.ui.UISettings;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * @author Konstantin Bulenkov
 */
final class ShowNavBarAction extends AnAction implements DumbAware, PopupAction, ActionRemoteBehaviorSpecification.Frontend {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final DataContext context = e.getDataContext();
    final Project project = CommonDataKeys.PROJECT.getData(context);
    if (project == null) {
      return;
    }
    showNavBar(e, project);
  }

  private static void showNavBar(@NotNull AnActionEvent e, @NotNull Project project) {
    UISettings uiSettings = UISettings.getInstance();
    if (NavBarIdeUtil.isNavbarShown(uiSettings)
        && (uiSettings.getShowStatusBar() || uiSettings.getNavBarLocation() != NavBarLocation.BOTTOM)) {
      SelectInNavBarTarget.selectInNavBar(true);
    }
    else {
      NavBarService.getInstance(project).showFloatingNavbar(e.getDataContext());
    }
  }

  @Override
  public void update(@NotNull final AnActionEvent e) {
    final boolean enabled = e.getData(CommonDataKeys.PROJECT) != null;
    e.getPresentation().setEnabled(enabled);

    // see RIDER-15982
    if (!ActionPlaces.isMainMenuOrActionSearch(e.getPlace())) {
      e.getPresentation().setText(ActionsBundle.messagePointer("action.ShowNavBar.ShortText"));
    }
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }
}