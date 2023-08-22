// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.dashboard.actions;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.dashboard.RunDashboardManager;
import com.intellij.execution.dashboard.RunDashboardRunConfigurationNode;
import com.intellij.execution.services.ServiceViewActionUtils;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class RemoveRunConfigurationTypeAction extends DumbAwareAction {

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Set<ConfigurationType> types = getTargetTypes(e);
    boolean isEnabled = e.getProject() != null && !types.isEmpty();

    Presentation presentation = e.getPresentation();
    presentation.setEnabledAndVisible(isEnabled);
    if (isEnabled) {
      presentation.setText(ExecutionBundle.messagePointer("run.dashboard.remove.run.configuration.type.action.name", types.size()));
    }
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) return;

    RunDashboardManager runDashboardManager = RunDashboardManager.getInstance(project);
    Set<String> types = new HashSet<>(runDashboardManager.getTypes());
    Set<ConfigurationType> targetTypes = getTargetTypes(e);
    for (ConfigurationType type : targetTypes) {
      types.remove(type.getId());
    }
    runDashboardManager.setTypes(types);
  }

  private static Set<ConfigurationType> getTargetTypes(AnActionEvent e) {
    List<RunDashboardRunConfigurationNode> nodes = ServiceViewActionUtils.getTargets(e, RunDashboardRunConfigurationNode.class);
    return ContainerUtil.map2Set(nodes, node -> node.getConfigurationSettings().getType());
  }
}
