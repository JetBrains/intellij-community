// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.dashboard.actions;

import com.intellij.execution.Executor;
import com.intellij.execution.ExecutorRegistry;
import com.intellij.execution.RunContentDescriptorId;
import com.intellij.execution.dashboard.RunDashboardManager;
import com.intellij.execution.dashboard.RunDashboardRunConfigurationNode;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.RunContentManager;
import com.intellij.execution.ui.RunContentManagerImpl;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.platform.execution.dashboard.RunDashboardManagerImpl;
import com.intellij.ui.content.Content;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.NotNull;

import static com.intellij.execution.dashboard.actions.RunDashboardActionUtils.getLeafTargets;

final class ClearContentAction extends DumbAwareAction
  implements ActionRemoteBehaviorSpecification.FrontendOtherwiseBackend {

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    Presentation presentation = e.getPresentation();
    if (project == null) {
      presentation.setEnabledAndVisible(false);
      return;
    }

    JBIterable<RunDashboardRunConfigurationNode> targetNodes = getLeafTargets(e);
    boolean enabled = targetNodes.filter(node -> {
      RunContentDescriptor descriptor = node.getDescriptor();
      if (descriptor == null) {
        return RunDashboardManagerImpl.getInstance(project).getPersistedStatus(
          node.getConfigurationSettings().getConfiguration()) != null;
      }
      ProcessHandler processHandler = descriptor.getProcessHandler();
      return processHandler == null || processHandler.isProcessTerminated();
    }).isNotEmpty();
    presentation.setEnabled(enabled);
    presentation.setVisible(enabled || !e.isFromContextMenu());
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) return;

    for (RunDashboardRunConfigurationNode node : getLeafTargets(e)) {
      RunContentDescriptor descriptor = node.getDescriptor();
      if (descriptor == null) {
        RunDashboardManager.getInstance(project).clearConfigurationStatus(
          node.getConfigurationSettings().getConfiguration());
        continue;
      }
      ProcessHandler processHandler = descriptor.getProcessHandler();
      if (processHandler != null && !processHandler.isProcessTerminated()) continue;

      Content content = descriptor.getAttachedContent();
      Executor executor = content == null ?
                          ExecutorRegistry.getInstance().getExecutorById(ToolWindowId.DEBUG) :
                          RunContentManagerImpl.getExecutorByContent(content);
      if (executor == null) continue;

      RunContentDescriptor managedDescriptor = getManagedDescriptor(descriptor, project);
      RunContentManager.getInstance(project).removeRunContent(executor, managedDescriptor);
      RunDashboardManager.getInstance(project).clearConfigurationStatus(
        node.getConfigurationSettings().getConfiguration());
    }
  }

  private static RunContentDescriptor getManagedDescriptor(RunContentDescriptor descriptor, Project project) {
    RunContentDescriptorId descriptorId = descriptor.getId();
    if (descriptorId == null) return descriptor;

    RunContentDescriptor updatedDescriptor =
    ContainerUtil.find(RunContentManager.getInstance(project).getRunContentDescriptors(),
                       managedDescriptor -> descriptorId.equals(managedDescriptor.getId()));
    return updatedDescriptor == null ? descriptor : updatedDescriptor;
  }
}
