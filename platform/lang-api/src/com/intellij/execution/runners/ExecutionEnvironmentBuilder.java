/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.execution.*;
import com.intellij.execution.configurations.ConfigurationPerRunnerSettings;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolderBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ExecutionEnvironmentBuilder {
  @NotNull private RunProfile myRunProfile;
  @NotNull private ExecutionTarget myTarget = DefaultExecutionTarget.INSTANCE;

  @NotNull private final Project myProject;

  @Nullable private RunnerSettings myRunnerSettings;
  @Nullable private ConfigurationPerRunnerSettings myConfigurationSettings;
  @Nullable private RunContentDescriptor myContentToReuse;
  @Nullable private RunnerAndConfigurationSettings myRunnerAndConfigurationSettings;
  @Nullable private String myRunnerId;
  private ProgramRunner<?> myRunner;
  private boolean myAssignNewId;
  @NotNull private Executor myExecutor;
  @Nullable private DataContext myDataContext;
  private final UserDataHolderBase myUserData = new UserDataHolderBase();

  public ExecutionEnvironmentBuilder(@NotNull Project project, @NotNull Executor executor) {
    myProject = project;
    myExecutor = executor;
  }

  @NotNull
  public static ExecutionEnvironmentBuilder create(@NotNull Project project, @NotNull Executor executor, @NotNull RunProfile runProfile) throws ExecutionException {
    ExecutionEnvironmentBuilder builder = createOrNull(project, executor, runProfile);
    if (builder == null) {
      throw new ExecutionException("Cannot find runner for " + runProfile.getName());
    }
    return builder;
  }

  @Nullable
  public static ExecutionEnvironmentBuilder createOrNull(@NotNull Project project, @NotNull Executor executor, @NotNull RunProfile runProfile) {
    ProgramRunner runner = RunnerRegistry.getInstance().getRunner(executor.getId(), runProfile);
    if (runner == null) {
      return null;
    }
    return new ExecutionEnvironmentBuilder(project, executor).runner(runner).runProfile(runProfile);
  }

  @Nullable
  public static ExecutionEnvironmentBuilder createOrNull(@NotNull Executor executor, @NotNull RunnerAndConfigurationSettings settings) {
    ExecutionEnvironmentBuilder builder = createOrNull(settings.getConfiguration().getProject(), executor, settings.getConfiguration());
    return builder == null ? null : builder.runnerAndSettings(builder.myRunner, settings);
  }

  @NotNull
  public static ExecutionEnvironmentBuilder create(@NotNull Executor executor, @NotNull RunnerAndConfigurationSettings settings) throws ExecutionException {
    RunConfiguration configuration = settings.getConfiguration();
    ExecutionEnvironmentBuilder builder = create(configuration.getProject(), executor, configuration);
    return builder.runnerAndSettings(builder.myRunner, settings);
  }

  @NotNull
  public static ExecutionEnvironmentBuilder create(@NotNull Executor executor, @NotNull RunConfiguration configuration) {
    return new ExecutionEnvironmentBuilder(configuration.getProject(), executor).runProfile(configuration);
  }

  @NotNull
  Executor getExecutor() {
    return myExecutor;
  }

  /**
   * Creates an execution environment builder initialized with a copy of the specified environment.
   *
   * @param copySource the environment to copy from.
   */
  public ExecutionEnvironmentBuilder(@NotNull ExecutionEnvironment copySource) {
    myTarget = copySource.getExecutionTarget();
    myProject = copySource.getProject();
    myRunnerAndConfigurationSettings = copySource.getRunnerAndConfigurationSettings();
    myRunProfile = copySource.getRunProfile();
    myRunnerSettings = copySource.getRunnerSettings();
    myConfigurationSettings = copySource.getConfigurationSettings();
    //noinspection deprecation
    myRunner = copySource.getRunner();
    myContentToReuse = copySource.getContentToReuse();
    myExecutor = copySource.getExecutor();
    copySource.copyUserDataTo(myUserData);
  }

  public ExecutionEnvironmentBuilder target(@Nullable ExecutionTarget target) {
    if (target != null) {
      myTarget = target;
    }
    return this;
  }

  public ExecutionEnvironmentBuilder activeTarget() {
    myTarget = ExecutionTargetManager.getActiveTarget(myProject);
    return this;
  }

  public ExecutionEnvironmentBuilder runnerAndSettings(@NotNull ProgramRunner runner,
                                                       @NotNull RunnerAndConfigurationSettings settings) {
    myRunnerAndConfigurationSettings = settings;
    myRunProfile = settings.getConfiguration();
    myRunnerSettings = settings.getRunnerSettings(runner);
    myConfigurationSettings = settings.getConfigurationSettings(runner);
    myRunner = runner;
    return this;
  }

  public ExecutionEnvironmentBuilder runnerSettings(@Nullable RunnerSettings runnerSettings) {
    myRunnerSettings = runnerSettings;
    return this;
  }

  public ExecutionEnvironmentBuilder contentToReuse(@Nullable RunContentDescriptor contentToReuse) {
    myContentToReuse = contentToReuse;
    return this;
  }

  public ExecutionEnvironmentBuilder runProfile(@NotNull RunProfile runProfile) {
    myRunProfile = runProfile;
    return this;
  }

  public ExecutionEnvironmentBuilder runner(@NotNull ProgramRunner<?> runner) {
    myRunner = runner;
    return this;
  }

  public ExecutionEnvironmentBuilder dataContext(@Nullable DataContext dataContext) {
    myDataContext = dataContext;
    return this;
  }

  public ExecutionEnvironmentBuilder executor(@NotNull Executor executor) {
    myExecutor = executor;
    return this;
  }

  @NotNull
  public ExecutionEnvironment build() {
    ExecutionEnvironment environment = null;
    ExecutionEnvironmentProvider environmentProvider = ServiceManager.getService(myProject, ExecutionEnvironmentProvider.class);
    if (environmentProvider != null) {
      environment = environmentProvider.createExecutionEnvironment(
        myProject, myRunProfile, myExecutor, myTarget, myRunnerSettings, myConfigurationSettings, myRunnerAndConfigurationSettings);
    }

    if (environment == null && myRunner == null) {
      if (myRunnerId == null) {
        myRunner = RunnerRegistry.getInstance().getRunner(myExecutor.getId(), myRunProfile);
      }
      else {
        myRunner = RunnerRegistry.getInstance().findRunnerById(myRunnerId);
      }
    }

    if (environment == null && myRunner == null) {
      throw new IllegalStateException("Runner must be specified");
    }

    if (environment == null) {
      environment = new ExecutionEnvironment(myRunProfile, myExecutor, myTarget, myProject, myRunnerSettings,
                                             myConfigurationSettings, myContentToReuse, myRunnerAndConfigurationSettings, myRunner);
    }

    if (myAssignNewId) {
      environment.assignNewExecutionId();
    }
    if (myDataContext != null) {
      environment.setDataContext(myDataContext);
    }
    myUserData.copyUserDataTo(environment);
    return environment;
  }

  public void buildAndExecute() throws ExecutionException {
    ExecutionEnvironment environment = build();
    myRunner.execute(environment);
  }
}
