// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.dashboard.actions;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.dashboard.RunDashboardManager;
import com.intellij.execution.dashboard.RunDashboardManagerImpl;
import com.intellij.execution.dashboard.RunDashboardRunConfigurationNode;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

final class HideConfigurationAction extends DumbAwareAction {

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    JBIterable<RunDashboardRunConfigurationNode> nodes = RunDashboardActionUtils.getTargets(e);
    boolean enabled = e.getProject() != null && nodes.isNotEmpty();
    Presentation presentation = e.getPresentation();
    presentation.setEnabledAndVisible(enabled);
    if (enabled) {
      presentation.setText(ExecutionBundle.message("run.dashboard.hide.configuration.action.name", nodes.size()));
    }
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) return;

    Set<RunConfiguration> configurations =
      RunDashboardActionUtils.getTargets(e).map(node -> node.getConfigurationSettings().getConfiguration()).toSet();
    ((RunDashboardManagerImpl)RunDashboardManager.getInstance(project)).hideConfigurations(configurations);
  }
}
