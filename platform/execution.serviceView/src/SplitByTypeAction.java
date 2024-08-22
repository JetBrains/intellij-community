// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.serviceView;

import com.intellij.execution.services.ServiceViewManager;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import static com.intellij.platform.execution.serviceView.ServiceViewActionProvider.getSelectedView;

final class SplitByTypeAction extends DumbAwareAction {

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    boolean isEnabled;
    if (e.isFromContextMenu()) {
      Project project = e.getProject();
      ServiceView selectedView = getSelectedView(e);
      isEnabled = project != null && selectedView != null &&
                  ((ServiceViewManagerImpl)ServiceViewManager.getInstance(project)).isSplitByTypeEnabled(selectedView);
    }
    else {
      isEnabled = false;
    }
    e.getPresentation().setEnabledAndVisible(isEnabled);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) return;

    ServiceView selectedView = getSelectedView(e);
    if (selectedView == null) return;

    ((ServiceViewManagerImpl)ServiceViewManager.getInstance(project)).splitByType(selectedView);
  }
}
