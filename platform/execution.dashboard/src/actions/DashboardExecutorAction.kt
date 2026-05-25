// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.dashboard.actions;

import com.intellij.execution.Executor;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.dashboard.RunDashboardRunConfigurationNode;
import com.intellij.execution.dashboard.actions.ExecutorAction;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@SuppressWarnings("removal")
abstract class DashboardExecutorAction extends ExecutorAction {
  private static final Key<List<Integer>> RUNNABLE_LEAVES_KEY = Key.create("RUN_DASHBOARD_RUNNABLE_LEAVES_KEY");

  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) {
      update(e, false);
      return;
    }

    List<RunDashboardRunConfigurationNode> targetNodes = RunDashboardActionSelection.getLeafTargets(e).toList();
    boolean running = isAnythingRunningInSelection(targetNodes) | isContextualDescriptorNotTerminated(e);
    update(e, running);

    List<Integer> runnableLeaves = getRunnableLeaves(targetNodes, project);
    Presentation presentation = e.getPresentation();
    if (!runnableLeaves.isEmpty()) {
      presentation.putClientProperty(RUNNABLE_LEAVES_KEY, runnableLeaves);
    }
    else {
      presentation.putClientProperty(RUNNABLE_LEAVES_KEY, null);
    }
    presentation.setEnabled(!runnableLeaves.isEmpty());
    presentation.setVisible(!targetNodes.isEmpty());
  }

  private static boolean isAnythingRunningInSelection(List<RunDashboardRunConfigurationNode> targetNodes) {
    return ContainerUtil.find(targetNodes, node -> {
      return isRunning(node);
    }) != null;
  }

  private static boolean isContextualDescriptorNotTerminated(@NotNull AnActionEvent e) {
    var descriptor = e.getData(LangDataKeys.RUN_CONTENT_DESCRIPTOR);
    ProcessHandler processHandler = descriptor == null ? null : descriptor.getProcessHandler();
    return processHandler != null && !processHandler.isProcessTerminated();
  }

  private List<Integer> getRunnableLeaves(List<RunDashboardRunConfigurationNode> targetNodes, @NotNull Project project) {
    List<Integer> runnableLeaves = new SmartList<>();
    for (int i = 0; i < targetNodes.size(); i++) {
      RunDashboardRunConfigurationNode node = targetNodes.get(i);
      if (canRun(node, project)) {
        runnableLeaves.add(i);
      }
    }
    return runnableLeaves;
  }

  private boolean canRun(@NotNull RunDashboardRunConfigurationNode node, @NotNull Project project) {
    ProgressManager.checkCanceled();
    RunnerAndConfigurationSettings settings = node.getConfigurationSettings();
    return settings != null && canRun(settings, null, DumbService.isDumb(project), getExecutor());
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) return;

    List<RunDashboardRunConfigurationNode> targetNodes = RunDashboardActionSelection.getLeafTargets(e).toList();
    List<Integer> runnableLeaves = e.getPresentation().getClientProperty(RUNNABLE_LEAVES_KEY);
    if (runnableLeaves == null) {
      runnableLeaves = getRunnableLeaves(targetNodes, project);
      if (runnableLeaves.isEmpty()) return;
    }

    Executor executor = getExecutor();
    for (int i : runnableLeaves) {
      if (targetNodes.size() > i) {
        RunDashboardRunConfigurationNode node = targetNodes.get(i);
        RunnerAndConfigurationSettings settings = node.getConfigurationSettings();
        if (settings == null) continue;
        RunContentDescriptor descriptor = node.getDescriptor();
        run(settings, descriptor, e.getDataContext(), executor);
      }
    }
  }
}
