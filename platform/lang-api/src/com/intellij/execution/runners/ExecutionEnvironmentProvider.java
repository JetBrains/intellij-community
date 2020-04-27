// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.runners;

import com.intellij.execution.ExecutionTarget;
import com.intellij.execution.Executor;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationPerRunnerSettings;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Vladislav.Soroka
 */
public interface ExecutionEnvironmentProvider {
  @Nullable
  ExecutionEnvironment createExecutionEnvironment(@NotNull Project project,
                                                  @NotNull RunProfile runProfile,
                                                  @NotNull Executor executor,
                                                  @NotNull ExecutionTarget target,
                                                  @Nullable RunnerSettings runnerSettings,
                                                  @Nullable ConfigurationPerRunnerSettings configurationSettings,
                                                  @Nullable RunnerAndConfigurationSettings settings);
}
