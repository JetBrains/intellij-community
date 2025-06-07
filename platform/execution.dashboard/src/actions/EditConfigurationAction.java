// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.dashboard.actions;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.RunManager;
import com.intellij.execution.dashboard.RunDashboardRunConfigurationNode;
import com.intellij.execution.dashboard.actions.RunDashboardActionUtils;
import com.intellij.execution.impl.RunDialog;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

final class EditConfigurationAction extends DumbAwareAction {

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    RunDashboardRunConfigurationNode node = project == null ? null : RunDashboardActionUtils.getTarget(e);
    boolean enabled = node != null && RunManager.getInstance(project).hasSettings(node.getConfigurationSettings());
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
    // fixme raspil dialog - ask Lera?
    Project project = e.getProject();
    RunDashboardRunConfigurationNode node = project == null ? null : RunDashboardActionUtils.getTarget(e);
    if (node == null) return;

    RunDialog.editConfiguration(project, node.getConfigurationSettings(),
                                ExecutionBundle.message("run.dashboard.edit.configuration.dialog.title"));
  }
}
