// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.dashboard.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.platform.execution.dashboard.splitApi.frontend.FrontendRunDashboardService;
import org.jetbrains.annotations.NotNull;

import static com.intellij.platform.execution.dashboard.actions.RunDashboardActionUtilsKt.getSelectedNode;
import static com.intellij.platform.execution.dashboard.actions.RunDashboardActionUtilsKt.scheduleEditConfiguration;

final class EditConfigurationAction extends DumbAwareAction implements ActionRemoteBehaviorSpecification.Frontend {

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    FrontendRunDashboardService node = project == null ? null : getSelectedNode(e);
    boolean enabled = node != null;
    Presentation presentation = e.getPresentation();
    presentation.setEnabled(enabled);
    boolean popupPlace = e.isFromContextMenu();
    presentation.setVisible(enabled || !popupPlace);
    if (popupPlace) {
      presentation.setText(getTemplatePresentation().getText() + "...");
    }
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    FrontendRunDashboardService node = project == null ? null : getSelectedNode(e);
    if (node == null) return;

    scheduleEditConfiguration(project, node.getRunDashboardServiceDto().getUuid());
  }
}
