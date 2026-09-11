// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.action.task;

import com.intellij.execution.Executor;
import com.intellij.execution.ExecutorRegistry;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.impl.statistics.RunConfigurationOptionUsagesCollector;
import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.action.ExternalSystemActionUtil;
import com.intellij.openapi.externalSystem.action.ExternalSystemNodeAction;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.execution.ExternalTaskExecutionInfo;
import com.intellij.openapi.externalSystem.model.task.TaskData;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.externalSystem.util.task.TaskExecutionSpec;
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

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

    ReadAction.nonBlocking(() -> findOrGet(context))
      .inSmartMode(project).expireWith(project)
      .finishOnUiThread(ModalityState.nonModal(), configuration -> {
        if (configuration == null || !runTaskAsExistingConfiguration(taskExecutionInfo, configuration)) {
          runTaskAsNewRunConfiguration(project,
                                       projectSystemId,
                                       taskExecutionInfo,
                                       newConfiguration -> {
                                         newConfiguration.setTemporary(true);
                                         RunConfigurationOptionUsagesCollector.logAddNew(context.getProject(), newConfiguration.getType().getId(), context.getPlace());
                                         context.getRunManager().addConfiguration(newConfiguration);
                                         context.getRunManager().setSelectedConfiguration(newConfiguration);
                                       });
          return;
        }
        context.getRunManager().addConfiguration(configuration);
        context.getRunManager().setSelectedConfiguration(configuration);
      })
      .submit(AppExecutorUtil.getAppExecutorService());
  }

  @RequiresReadLock
  private static @Nullable RunnerAndConfigurationSettings findOrGet(@NotNull ConfigurationContext context) {
    RunnerAndConfigurationSettings result = context.findExisting();
    if (result == null) {
      result = context.getConfiguration();
      if (result != null) {
        result.setTemporary(true);
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
                                                   @NotNull ExternalTaskExecutionInfo taskExecutionInfo,
                                                   @NotNull Consumer<RunnerAndConfigurationSettings> consumer) {
    TaskExecutionSpec spec = TaskExecutionSpec.create()
      .withProject(project)
      .withSystemId(projectSystemId)
      .withExecutorId(taskExecutionInfo.getExecutorId())
      .withSettings(taskExecutionInfo.getSettings())
      .withRunConfigurationConsumer(consumer)
      .withActivateToolWindowBeforeRun(true)
      .build();
    ExternalSystemUtil.runTask(spec);
  }
}
