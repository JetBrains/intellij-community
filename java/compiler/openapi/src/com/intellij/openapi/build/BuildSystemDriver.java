/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.openapi.build;

import com.intellij.execution.ExecutionTarget;
import com.intellij.execution.Executor;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationPerRunnerSettings;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * TODO
 * get compiled files status
 *
 * @author Vladislav.Soroka
 * @since 4/29/2016
 */
public abstract class BuildSystemDriver {

  public static final ExtensionPointName<BuildSystemDriver> EP_NAME = ExtensionPointName.create("com.intellij.buildSystemDriver");

  public abstract void build(@NotNull BuildContext buildContext, @Nullable BuildChunkStatusNotification callback);

  public abstract boolean canBuild(@NotNull BuildTarget buildTarget);

  public abstract boolean canRun(@NotNull String executorId, @NotNull RunProfile runProfile);

  public abstract ExecutionEnvironment createExecutionEnvironment(@NotNull RunProfile runProfile,
                                                                  @NotNull Executor executor,
                                                                  @NotNull ExecutionTarget executionTarget,
                                                                  @NotNull Project project,
                                                                  @Nullable RunnerSettings runnerSettings,
                                                                  @Nullable ConfigurationPerRunnerSettings configurationSettings,
                                                                  @Nullable RunnerAndConfigurationSettings settings);
}
