// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.services;

import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import static com.intellij.execution.services.ServiceViewActionProvider.getSelectedView;

final class SplitByTypeAction extends DumbAwareAction {

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    boolean isEnabled;
    if (ActionPlaces.isPopupPlace(e.getPlace())) {
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
