/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.execution.dashboard.actions;

import com.intellij.execution.*;
import com.intellij.execution.configurations.RuntimeConfigurationError;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.execution.dashboard.RunDashboardRunConfigurationNode;
import com.intellij.execution.dashboard.RunDashboardManager;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.RunContentManagerImpl;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.ui.content.Content;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;

/**
 * @author konstantin.aleev
 */
public abstract class ExecutorAction extends RunDashboardTreeLeafAction<RunDashboardRunConfigurationNode> {
  protected ExecutorAction(String text, String description, Icon icon) {
    super(text, description, icon);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) {
      update(e, false);
      return;
    }
    List<RunDashboardRunConfigurationNode> targetNodes = getTargetNodes(e);
    if (RunDashboardManager.getInstance(project).isShowConfigurations()) {
      boolean running = targetNodes.stream().anyMatch(node -> {
        Content content = node.getContent();
        return content != null && !RunContentManagerImpl.isTerminated(content);
      });
      update(e, running);
      e.getPresentation().setEnabled(targetNodes.stream().anyMatch(this::canRun));
    }
    else {
      Content content = RunDashboardManager.getInstance(project).getDashboardContentManager().getSelectedContent();
      update(e, content != null && !RunContentManagerImpl.isTerminated(content));
      e.getPresentation().setEnabled(content != null);
    }
  }

  private boolean canRun(RunDashboardRunConfigurationNode node) {
    final String executorId = getExecutor().getId();
    final RunnerAndConfigurationSettings configurationSettings = node.getConfigurationSettings();
    final ProgramRunner runner = ProgramRunnerUtil.getRunner(executorId, configurationSettings);
    final ExecutionTarget target = ExecutionTargetManager.getActiveTarget(node.getProject());

    return isValid(node) &&
           runner != null &&
           runner.canRun(executorId, configurationSettings.getConfiguration()) &&
           ExecutionTargetManager.canRun(configurationSettings, target) &&
           !isStarting(node.getProject(), configurationSettings, executorId, runner.getRunnerId());
  }

  private static boolean isStarting(Project project, RunnerAndConfigurationSettings configurationSettings, String executorId, String runnerId) {
    ExecutorRegistry executorRegistry = ExecutorRegistry.getInstance();
    if (executorRegistry.isStarting(project, executorId, runnerId)) return true;

    if (!configurationSettings.isSingleton()) return false;

    for (Executor executor : executorRegistry.getRegisteredExecutors()) {
      if (executor.getId().equals(executorId)) continue;

      ProgramRunner runner = ProgramRunnerUtil.getRunner(executor.getId(), configurationSettings);
      if (runner == null) continue;

      if (executorRegistry.isStarting(project, executor.getId(), runner.getRunnerId())) return true;
    }
    return false;
  }

  private boolean isValid(RunDashboardRunConfigurationNode node) {
    try {
      node.getConfigurationSettings().checkSettings(getExecutor());
      return true;
    }
    catch (IndexNotReadyException ex) {
      return true;
    }
    catch (RuntimeConfigurationError ex) {
      return false;
    }
    catch (RuntimeConfigurationException ex) {
      return true;
    }
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null || RunDashboardManager.getInstance(project).isShowConfigurations()) {
      super.actionPerformed(e);
    }
    else {
      Content content = RunDashboardManager.getInstance(project).getDashboardContentManager().getSelectedContent();
      if (content != null) {
        RunContentDescriptor descriptor = RunContentManagerImpl.getRunContentDescriptorByContent(content);
        JComponent component = content.getComponent();
        if (component == null) {
          return;
        }
        ExecutionEnvironment environment = LangDataKeys.EXECUTION_ENVIRONMENT.getData(DataManager.getInstance().getDataContext(component));
        if (environment == null) {
          return;
        }
        ExecutionManager.getInstance(project).restartRunProfile(project,
                                                                getExecutor(),
                                                                ExecutionTargetManager.getActiveTarget(project),
                                                                environment.getRunnerAndConfigurationSettings(),
                                                                descriptor == null ? null : descriptor.getProcessHandler());
      }
    }
  }

  @Override
  protected void doActionPerformed(RunDashboardRunConfigurationNode node) {
    if (!canRun(node)) return;

    RunContentDescriptor descriptor = node.getDescriptor();
    ExecutionManager.getInstance(node.getProject()).restartRunProfile(node.getProject(),
                                                                      getExecutor(),
                                                                      ExecutionTargetManager.getActiveTarget(node.getProject()),
                                                                      node.getConfigurationSettings(),
                                                                      descriptor == null ? null : descriptor.getProcessHandler());
  }

  @Override
  protected Class<RunDashboardRunConfigurationNode> getTargetNodeClass() {
    return RunDashboardRunConfigurationNode.class;
  }

  protected abstract Executor getExecutor();

  protected abstract void update(@NotNull AnActionEvent e, boolean running);
}
