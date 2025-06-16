// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.dashboard.actions;

import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.dashboard.RunDashboardRunConfigurationNode;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import static com.intellij.execution.dashboard.actions.RunDashboardActionUtils.getTarget;

final class RestoreConfigurationAction
  extends DumbAwareAction
  implements ActionRemoteBehaviorSpecification.Frontend {

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    RunDashboardRunConfigurationNode node = project == null ? null : getTarget(e);
    boolean enabled = node != null && !RunManager.getInstance(project).hasSettings(node.getConfigurationSettings());
    Presentation presentation = e.getPresentation();
    presentation.setEnabled(enabled);
    boolean popupPlace = e.isFromContextMenu();
    presentation.setVisible(enabled || !popupPlace);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    RunDashboardRunConfigurationNode node = project == null ? null : getTarget(e);
    if (node == null) return;

    // todo delegate to backend - ask Lera?
    RunManager runManager = RunManager.getInstance(project);
    RunnerAndConfigurationSettings settings = node.getConfigurationSettings();
    runManager.setUniqueNameIfNeeded(settings.getConfiguration());
    if (settings.isTemporary()) {
      runManager.setTemporaryConfiguration(settings);
    }
    else {
      runManager.addConfiguration(settings);
    }
  }
}
