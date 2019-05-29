// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.services;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class SplitByTypeAction extends DumbAwareAction {
  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }

    ServiceViewManagerImpl serviceViewManager = (ServiceViewManagerImpl)ServiceViewManager.getInstance(project);
    ServiceView selectedView = serviceViewManager.getSelectedView();
    if (selectedView == null || selectedView.getModel().getFilter() != null) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }

    Presentation presentation = e.getPresentation();
    presentation.setVisible(true);
    presentation.setEnabled(serviceViewManager.isSplitByTypeEnabled());
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) return;

    ((ServiceViewManagerImpl)ServiceViewManager.getInstance(project)).splitByType();
  }
}
