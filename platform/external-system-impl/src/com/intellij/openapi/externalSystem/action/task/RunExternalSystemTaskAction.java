// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.action.task;

import com.intellij.execution.Executor;
import com.intellij.execution.ExecutorRegistry;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.impl.statistics.RunConfigurationOptionUsagesCollector;
import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.action.ExternalSystemActionUtil;
import com.intellij.openapi.externalSystem.action.ExternalSystemNodeAction;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.execution.ExternalTaskExecutionInfo;
import com.intellij.openapi.externalSystem.model.task.TaskData;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Vladislav.Soroka
 */
@ApiStatus.Internal
public class RunExternalSystemTaskAction extends ExternalSystemNodeAction<TaskData> {

  private static final Logger LOG = Logger.getInstance(RunExternalSystemTaskAction.class);

  public RunExternalSystemTaskAction() {
    super(TaskData.class);
  }

  @Override
  protected void perform(@NotNull Project project,
                         @NotNull ProjectSystemId projectSystemId,
                         @NotNull TaskData taskData,
                         @NotNull AnActionEvent e) {
    final ExternalTaskExecutionInfo taskExecutionInfo = ExternalSystemActionUtil.buildTaskInfo(taskData);
    final ConfigurationContext context = ConfigurationContext.getFromContext(e.getDataContext(), e.getPlace());

    RunnerAndConfigurationSettings configuration = findOrGet(context);
    if (configuration == null ||
        !runTaskAsExistingConfiguration(taskExecutionInfo, configuration)) {
      runTaskAsNewRunConfiguration(project, projectSystemId, taskExecutionInfo);
      configuration = findOrGet(context); // if created during runTaskAsNewRunConfiguration
    }

    context.getRunManager().setSelectedConfiguration(configuration);
  }

  private static @Nullable RunnerAndConfigurationSettings findOrGet(@NotNull ConfigurationContext context) {
    RunnerAndConfigurationSettings result = context.findExisting();
    if (result == null) {
      result = context.getConfiguration();
      if (result != null) {
        context.getRunManager().setTemporaryConfiguration(result);
        RunConfigurationOptionUsagesCollector.logAddNew(context.getProject(), result.getType().getId(), context.getPlace());
      }
    }
    return result;
  }

  private static boolean runTaskAsExistingConfiguration(@NotNull ExternalTaskExecutionInfo taskExecutionInfo,
                                                        @NotNull RunnerAndConfigurationSettings configuration) {
    final String executorId = taskExecutionInfo.getExecutorId();
    Executor executor = ExecutorRegistry.getInstance().getExecutorById(executorId);
    if (executor == null) {
      return false;
    }
    ExecutionUtil.runConfiguration(configuration, executor);
    return true;
  }

  private static void runTaskAsNewRunConfiguration(@NotNull Project project,
                                                   @NotNull ProjectSystemId projectSystemId,
                                                   @NotNull ExternalTaskExecutionInfo taskExecutionInfo) {
    ExternalSystemUtil.runTask(taskExecutionInfo.getSettings(), taskExecutionInfo.getExecutorId(), project, projectSystemId);
  }
}
