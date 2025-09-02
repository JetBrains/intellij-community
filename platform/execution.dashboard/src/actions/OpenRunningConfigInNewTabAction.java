// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.dashboard.actions;

import com.intellij.execution.dashboard.RunDashboardManager;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.platform.execution.dashboard.RunDashboardManagerImpl;
import org.jetbrains.annotations.NotNull;

final class OpenRunningConfigInNewTabAction extends ToggleAction implements DumbAware, ActionRemoteBehaviorSpecification.Frontend {

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public boolean isSelected(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) return false;

    return ((RunDashboardManagerImpl)RunDashboardManager.getInstance(project)).isOpenRunningConfigInNewTab();
  }

  @Override
  public void setSelected(@NotNull AnActionEvent e, boolean state) {
    Project project = e.getProject();
    if (project == null) return;

    ((RunDashboardManagerImpl)RunDashboardManager.getInstance(project)).setOpenRunningConfigInNewTab(state);
  }
}
