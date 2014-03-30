/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.execution.runners;

import com.intellij.execution.DefaultExecutionTarget;
import com.intellij.execution.ExecutionTarget;
import com.intellij.execution.Executor;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationPerRunnerSettings;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User: Vassiliy.Kudryashov
 */
public final class ExecutionEnvironmentBuilder {
  private static final Logger LOG = Logger.getInstance("#com.intellij.execution.runners.ExecutionEnvironmentBuilder");
  @NotNull private RunProfile myRunProfile;
  @NotNull private ExecutionTarget myTarget = DefaultExecutionTarget.INSTANCE;

  @NotNull private final Project myProject;

  @Nullable private RunnerSettings myRunnerSettings;
  @Nullable private ConfigurationPerRunnerSettings myConfigurationSettings;
  @Nullable private RunContentDescriptor myContentToReuse;
  @Nullable private RunnerAndConfigurationSettings myRunnerAndConfigurationSettings;
  @Nullable private String myRunnerId;
  private boolean myAssignNewId;
  @NotNull private final Executor myExecutor;
  @Nullable private DataContext myDataContext;

  public ExecutionEnvironmentBuilder(@NotNull Project project, @NotNull Executor executor) {
    myProject = project;
    myExecutor = executor;
  }

  /**
   * Creates an execution environment builder initialized with a copy of the specified environment.
   *
   * @param copySource the environment to copy from.
   */
  public ExecutionEnvironmentBuilder(@NotNull ExecutionEnvironment copySource) {
    setTarget(copySource.getExecutionTarget());
    myProject = copySource.getProject();
    myRunnerAndConfigurationSettings = copySource.getRunnerAndConfigurationSettings();
    myRunProfile = copySource.getRunProfile();
    myRunnerSettings = copySource.getRunnerSettings();
    myConfigurationSettings = copySource.getConfigurationSettings();
    myRunnerId = copySource.getRunnerId();
    setContentToReuse(copySource.getContentToReuse());
    myExecutor = copySource.getExecutor();
  }

  public ExecutionEnvironmentBuilder setTarget(@NotNull ExecutionTarget target) {
    myTarget = target;
    return this;
  }

  public ExecutionEnvironmentBuilder setRunnerAndSettings(@NotNull ProgramRunner programRunner,
                                                          @NotNull RunnerAndConfigurationSettings settings) {
    myRunnerAndConfigurationSettings = settings;
    setRunProfile(settings.getConfiguration());
    setRunnerSettings(settings.getRunnerSettings(programRunner));
    setConfigurationSettings(settings.getConfigurationSettings(programRunner));
    setRunnerId(programRunner.getRunnerId());
    return this;
  }

  public ExecutionEnvironmentBuilder setRunnerSettings(@Nullable RunnerSettings runnerSettings) {
    myRunnerSettings = runnerSettings;
    return this;
  }

  public ExecutionEnvironmentBuilder setConfigurationSettings(@Nullable ConfigurationPerRunnerSettings configurationSettings) {
    myConfigurationSettings = configurationSettings;
    return this;
  }

  public ExecutionEnvironmentBuilder setContentToReuse(@Nullable RunContentDescriptor contentToReuse) {
    myContentToReuse = contentToReuse;
    return this;
  }

  public ExecutionEnvironmentBuilder setRunProfile(@NotNull RunProfile runProfile) {
    myRunProfile = runProfile;
    return this;
  }

  public ExecutionEnvironmentBuilder setRunnerId(@Nullable String runnerId) {
    myRunnerId = runnerId;
    return this;
  }

  public ExecutionEnvironmentBuilder assignNewId() {
    myAssignNewId = true;
    return this;
  }

  public ExecutionEnvironmentBuilder setDataContext(@Nullable DataContext dataContext) {
    myDataContext = dataContext;
    return this;
  }

  @NotNull
  public ExecutionEnvironment build() {
    ExecutionEnvironment environment =
      new ExecutionEnvironment(myRunProfile, myExecutor, myTarget, myProject, myRunnerSettings, myConfigurationSettings, myContentToReuse,
                               myRunnerAndConfigurationSettings, myRunnerId);
    if (myAssignNewId) {
      environment.assignNewExecutionId();
    }
    if (myDataContext != null) {
      environment.setDataContext(myDataContext);
    }
    return environment;
  }
}
