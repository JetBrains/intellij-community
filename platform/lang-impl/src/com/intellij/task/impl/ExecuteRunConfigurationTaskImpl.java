// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.task.impl;

import com.intellij.execution.ExecutionTarget;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationPerRunnerSettings;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.lang.LangBundle;
import com.intellij.task.ExecuteRunConfigurationTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Vladislav.Soroka
 */
public final class ExecuteRunConfigurationTaskImpl extends AbstractProjectTask implements ExecuteRunConfigurationTask {
  private final @NotNull RunProfile myRunProfile;
  private @Nullable ExecutionTarget myTarget;
  private @Nullable RunnerSettings myRunnerSettings;
  private @Nullable ConfigurationPerRunnerSettings myConfigurationSettings;
  private @Nullable RunnerAndConfigurationSettings mySettings;

  public ExecuteRunConfigurationTaskImpl(@NotNull RunProfile runProfile) {
    myRunProfile = runProfile;
  }

  public ExecuteRunConfigurationTaskImpl(@NotNull RunProfile runProfile,
                                         @NotNull ExecutionTarget target,
                                         @Nullable RunnerSettings runnerSettings,
                                         @Nullable ConfigurationPerRunnerSettings configurationSettings,
                                         @Nullable RunnerAndConfigurationSettings settings) {
    myRunProfile = runProfile;
    myTarget = target;
    myRunnerSettings = runnerSettings;
    myConfigurationSettings = configurationSettings;
    mySettings = settings;
  }

  @Override
  public @NotNull RunProfile getRunProfile() {
    return myRunProfile;
  }

  @Override
  public @Nullable ExecutionTarget getExecutionTarget() {
    return myTarget;
  }

  @Override
  public @Nullable RunnerSettings getRunnerSettings() {
    return myRunnerSettings;
  }

  @Override
  public @Nullable ConfigurationPerRunnerSettings getConfigurationSettings() {
    return myConfigurationSettings;
  }

  @Override
  public @Nullable RunnerAndConfigurationSettings getSettings() {
    return mySettings;
  }

  @Override
  public @NotNull String getPresentableName() {
    return LangBundle.message("project.task.name.run.task.0", myRunProfile.getName());
  }
}
