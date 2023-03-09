// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.navigationToolbar;

import com.intellij.ide.navbar.ide.NavBarIdeUtil;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.impl.IdeRootPane;
import com.intellij.openapi.wm.impl.WindowManagerImpl;
import com.intellij.openapi.wm.impl.status.IdeStatusBarImpl;
import org.jetbrains.annotations.NotNull;

/**
 * @author Anna Kozlova
 * @author Konstantin Bulenkov
 * @deprecated unused in ide.navBar.v2
 */
@Deprecated
final class ActivateNavigationBarAction extends AnAction implements DumbAware {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null || !UISettings.getInstance().getShowNavigationBar()) {
      return;
    }

    IdeRootPane ideRootPane = ((WindowManagerImpl)WindowManager.getInstance()).getProjectFrameRootPane(project);
    if (ideRootPane == null) {
      return;
    }

    var component = ideRootPane.findNorthUiComponentByKey(IdeStatusBarImpl.NAVBAR_WIDGET_KEY);
    if (component instanceof NavBarPanel) {
      ((NavBarPanel)component).rebuildAndSelectTail(true);
    }
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    final Project project = e.getProject();
    UISettings settings = UISettings.getInstance();
    final boolean enabled = project != null && NavBarIdeUtil.isNavbarShown(settings);
    e.getPresentation().setEnabled(enabled);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }
}
