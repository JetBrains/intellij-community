// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.navigationToolbar;

import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.navbar.ide.NavBarIdeUtil;
import com.intellij.ide.navbar.ide.NavBarService;
import com.intellij.ide.ui.NavBarLocation;
import com.intellij.ide.ui.UISettings;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.ui.ComponentUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class ShowNavBarAction extends AnAction implements DumbAware, PopupAction {

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final DataContext context = e.getDataContext();
    final Project project = CommonDataKeys.PROJECT.getData(context);
    if (project == null) {
      return;
    }
    if (NavBarIdeUtil.isNavbarV2Enabled()) {
      showNavBarV2(e, project);
    }
    else {
      showNavBar(e, project);
    }
  }

  private static void showNavBar(@NotNull AnActionEvent e, @NotNull Project project) {
    final DataContext context = e.getDataContext();
    UISettings uiSettings = UISettings.getInstance();
    if (NavBarIdeUtil.isNavbarShown(uiSettings)
      && (uiSettings.getShowStatusBar() || uiSettings.getNavBarLocation() != NavBarLocation.BOTTOM)){SelectInNavBarTarget.selectInNavBar(true);
    }
    else {
      final Component component = PlatformCoreDataKeys.CONTEXT_COMPONENT.getData(context);
      if (!isInsideNavBar(component)) {
        IdeEventQueue.getInstance().getPopupManager().closeAllPopups(false);
        final Editor editor = CommonDataKeys.EDITOR.getData(context);
        final NavBarPanel toolbarPanel = new NavBarPanel(project, false);
        toolbarPanel.showHint(editor, context);
      }
    }
  }

  private static void showNavBarV2(@NotNull AnActionEvent e, @NotNull Project project) {
    DataContext context = e.getDataContext();
    NavBarService.getInstance(project).jumpToNavbar(context);
  }

  private static boolean isInsideNavBar(Component c) {
    return c == null
           || c instanceof NavBarPanel
           || ComponentUtil.getParentOfType((Class<? extends NavBarListWrapper>)NavBarListWrapper.class, c) != null;
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