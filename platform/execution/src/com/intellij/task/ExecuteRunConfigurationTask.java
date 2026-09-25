// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.task;

import com.intellij.execution.ExecutionTarget;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationPerRunnerSettings;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunnerSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Vladislav.Soroka
 */
public interface ExecuteRunConfigurationTask extends ProjectTask {

  @NotNull
  RunProfile getRunProfile();

  @Nullable
  ExecutionTarget getExecutionTarget();

  @Nullable
  RunnerSettings getRunnerSettings();

  @Nullable
  ConfigurationPerRunnerSettings getConfigurationSettings();

  @Nullable
  RunnerAndConfigurationSettings getSettings();
}
