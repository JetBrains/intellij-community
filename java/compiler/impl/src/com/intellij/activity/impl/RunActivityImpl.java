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
package com.intellij.activity.impl;

import com.intellij.activity.RunActivity;
import com.intellij.execution.ExecutionTarget;
import com.intellij.execution.Executor;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationPerRunnerSettings;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunnerSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Vladislav.Soroka
 * @since 7/13/2016
 */
public class RunActivityImpl extends AbstractActivity implements RunActivity {
  @NotNull private final RunProfile myRunProfile;
  @Nullable private Executor myExecutor;
  @Nullable private ExecutionTarget myTarget;
  @Nullable private RunnerSettings myRunnerSettings;
  @Nullable private ConfigurationPerRunnerSettings myConfigurationSettings;
  @Nullable private RunnerAndConfigurationSettings mySettings;

  public RunActivityImpl(@NotNull RunProfile runProfile) {
    myRunProfile = runProfile;
  }

  public RunActivityImpl(@NotNull RunProfile runProfile,
                         @NotNull Executor executor,
                         @NotNull ExecutionTarget target,
                         @Nullable RunnerSettings runnerSettings,
                         @Nullable ConfigurationPerRunnerSettings configurationSettings,
                         @Nullable RunnerAndConfigurationSettings settings) {
    myRunProfile = runProfile;
    myExecutor = executor;
    myTarget = target;
    myRunnerSettings = runnerSettings;
    myConfigurationSettings = configurationSettings;
    mySettings = settings;
  }

  @NotNull
  @Override
  public RunProfile getRunProfile() {
    return myRunProfile;
  }

  @Nullable
  @Override
  public Executor getExecutor() {
    return myExecutor;
  }

  @Nullable
  @Override
  public ExecutionTarget getExecutionTarget() {
    return myTarget;
  }

  @Nullable
  @Override
  public RunnerSettings getRunnerSettings() {
    return myRunnerSettings;
  }

  @Nullable
  @Override
  public ConfigurationPerRunnerSettings getConfigurationSettings() {
    return myConfigurationSettings;
  }

  @Nullable
  @Override
  public RunnerAndConfigurationSettings getSettings() {
    return mySettings;
  }
}
