// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.task.impl;

import com.intellij.execution.ExecutionTarget;
import com.intellij.execution.Executor;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.*;
import com.intellij.execution.impl.ExecutionManagerImpl;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionEnvironmentProvider;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.task.ExecuteRunConfigurationTask;
import com.intellij.task.ProjectTaskRunner;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Vladislav.Soroka
 */
public final class ExecutionEnvironmentProviderImpl implements ExecutionEnvironmentProvider {
  private static final Logger LOG = Logger.getInstance(ExecutionEnvironmentProvider.class);

  @Nullable
  @Override
  public ExecutionEnvironment createExecutionEnvironment(@NotNull Project project,
                                                         @NotNull RunProfile runProfile,
                                                         @NotNull Executor executor,
                                                         @NotNull ExecutionTarget target,
                                                         @Nullable RunnerSettings runnerSettings,
                                                         @Nullable ConfigurationPerRunnerSettings configurationSettings,
                                                         @Nullable RunnerAndConfigurationSettings settings) {
    ExecuteRunConfigurationTask
      runTask = new ExecuteRunConfigurationTaskImpl(runProfile, target, runnerSettings, configurationSettings, settings);
    ExecutionEnvironment environment = ProjectTaskRunner.EP_NAME.computeSafeIfAny(projectTaskRunner -> {
      try {
        if (projectTaskRunner.canRun(project, runTask)) {
          return projectTaskRunner.createExecutionEnvironment(project, runTask, executor);
        }
      }
      catch (ProcessCanceledException e) {
        throw e;
      }
      catch (Exception e) {
        LOG.error("Broken project task runner: " + projectTaskRunner.getClass().getName(), e);
      }

      return null;
    });
    if (environment != null) {
      RunProfile environmentRunProfile = environment.getRunProfile();
      ExecutionManagerImpl.setDelegatedRunProfile(environmentRunProfile, runProfile);
      copySettings(settings, environment);
      copyCommonRunProfileOptions(runProfile, environmentRunProfile);
    }
    return environment;
  }

  private static void copySettings(@Nullable RunnerAndConfigurationSettings settings, @NotNull ExecutionEnvironment environment) {
    if (settings != null) {
      RunnerAndConfigurationSettings environmentSettings = environment.getRunnerAndConfigurationSettings();
      if (environmentSettings != null && environmentSettings != settings) {
        environmentSettings.setActivateToolWindowBeforeRun(settings.isActivateToolWindowBeforeRun());
        environmentSettings.setEditBeforeRun(settings.isEditBeforeRun());
      }
    }
  }

  private static void copyCommonRunProfileOptions(@NotNull RunProfile runProfile, @NotNull RunProfile environmentRunProfile) {
    if (environmentRunProfile instanceof RunConfiguration && runProfile instanceof RunConfiguration) {
      ((RunConfiguration)environmentRunProfile).setAllowRunningInParallel(((RunConfiguration)runProfile).isAllowRunningInParallel());
    }

    if (environmentRunProfile instanceof RunConfigurationBase && runProfile instanceof RunConfigurationBase) {
      for (LogFileOptions logFile : ((RunConfigurationBase<?>)runProfile).getLogFiles()) {
        if (!((RunConfigurationBase<?>)environmentRunProfile).getLogFiles().contains(logFile)) {
          ((RunConfigurationBase<?>)environmentRunProfile).addLogFile(logFile.getPathPattern(), logFile.getName(), logFile.isEnabled(),
                                                                      logFile.isSkipContent(), logFile.isShowAll());
        }
      }
    }
  }
}
