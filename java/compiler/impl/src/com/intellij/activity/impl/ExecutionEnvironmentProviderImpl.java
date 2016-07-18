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

import com.intellij.activity.ActivityRunner;
import com.intellij.activity.RunActivity;
import com.intellij.execution.ExecutionTarget;
import com.intellij.execution.Executor;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationPerRunnerSettings;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionEnvironmentProvider;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Vladislav.Soroka
 * @since 5/11/2016
 */
public class ExecutionEnvironmentProviderImpl implements ExecutionEnvironmentProvider {

  @Nullable
  @Override
  public ExecutionEnvironment createActivityExecutionEnvironment(@NotNull Project project,
                                                                 @NotNull RunProfile runProfile,
                                                                 @NotNull Executor executor,
                                                                 @NotNull ExecutionTarget target,
                                                                 @Nullable RunnerSettings runnerSettings,
                                                                 @Nullable ConfigurationPerRunnerSettings configurationSettings,
                                                                 @Nullable RunnerAndConfigurationSettings settings) {

    RunActivity runActivity = new RunActivityImpl(runProfile, executor, target, runnerSettings, configurationSettings, settings);
    for (ActivityRunner activityRunner : ActivityRunner.EP_NAME.getExtensions()) {
      if (activityRunner.canRun(runActivity)) {
        return activityRunner.createActivityExecutionEnvironment(project, runActivity);
      }
    }
    return null;
  }
}
