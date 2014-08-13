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

import com.intellij.execution.*;
import com.intellij.execution.configurations.ConfigurationPerRunnerSettings;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User: Vassiliy.Kudryashov
 */
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

  public ExecutionEnvironmentBuilder(@NotNull Project project, @NotNull Executor executor) {
    myProject = project;
    myExecutor = executor;
  }

  @NotNull
  public static ExecutionEnvironmentBuilder create(@NotNull Project project, @NotNull Executor executor, @NotNull RunProfile runProfile) throws ExecutionException {
    ProgramRunner runner = RunnerRegistry.getInstance().getRunner(executor.getId(), runProfile);
    if (runner == null) {
      throw new ExecutionException("Cannot find runner for " + runProfile.getName());
    }
    return new ExecutionEnvironmentBuilder(project, executor).runner(runner).runProfile(runProfile);
  }

  @NotNull
  public static ExecutionEnvironmentBuilder create(@NotNull Executor executor, @NotNull RunnerAndConfigurationSettings settings) throws ExecutionException {
    ExecutionEnvironmentBuilder builder = create(settings.getConfiguration().getProject(), executor, settings.getConfiguration());
    return builder.runnerAndSettings(builder.myRunner, settings);
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
    myRunnerId = copySource.getRunnerId();
    myRunner = copySource.myRunner;
    myContentToReuse = copySource.getContentToReuse();
    myExecutor = copySource.getExecutor();
  }

  @SuppressWarnings("UnusedDeclaration")
  @Deprecated
  /**
   * to remove in IDEA 15
   */
  public ExecutionEnvironmentBuilder setTarget(@NotNull ExecutionTarget target) {
    return target(target);
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

  @SuppressWarnings("UnusedDeclaration")
  @Deprecated
  /**
   * to remove in IDEA 15
   */
  public ExecutionEnvironmentBuilder setRunnerAndSettings(@NotNull ProgramRunner programRunner,
                                                          @NotNull RunnerAndConfigurationSettings settings) {
    return runnerAndSettings(programRunner, settings);
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

  @SuppressWarnings("UnusedDeclaration")
  @Deprecated
  /**
   * to remove in IDEA 15
   */
  public ExecutionEnvironmentBuilder setRunnerSettings(@Nullable RunnerSettings runnerSettings) {
    myRunnerSettings = runnerSettings;
    return this;
  }

  public ExecutionEnvironmentBuilder runnerSettings(@Nullable RunnerSettings runnerSettings) {
    myRunnerSettings = runnerSettings;
    return this;
  }

  @SuppressWarnings("UnusedDeclaration")
  @Deprecated
  /**
   * to remove in IDEA 15
   */
  public ExecutionEnvironmentBuilder setConfigurationSettings(@Nullable ConfigurationPerRunnerSettings configurationSettings) {
    myConfigurationSettings = configurationSettings;
    return this;
  }

  @SuppressWarnings("UnusedDeclaration")
  @Deprecated
  /**
   * to remove in IDEA 15
   */
  public ExecutionEnvironmentBuilder setContentToReuse(@Nullable RunContentDescriptor contentToReuse) {
    contentToReuse(contentToReuse);
    return this;
  }

  public ExecutionEnvironmentBuilder contentToReuse(@Nullable RunContentDescriptor contentToReuse) {
    myContentToReuse = contentToReuse;
    return this;
  }

  public ExecutionEnvironmentBuilder setRunProfile(@NotNull RunProfile runProfile) {
    return runProfile(runProfile);
  }

  public ExecutionEnvironmentBuilder runProfile(@NotNull RunProfile runProfile) {
    myRunProfile = runProfile;
    return this;
  }

  @SuppressWarnings("UnusedDeclaration")
  @Deprecated
  /**
   * to remove in IDEA 15
   */
  public ExecutionEnvironmentBuilder setRunnerId(@Nullable String runnerId) {
    myRunnerId = runnerId;
    return this;
  }

  public ExecutionEnvironmentBuilder runner(@NotNull ProgramRunner<?> runner) {
    myRunner = runner;
    return this;
  }

  public ExecutionEnvironmentBuilder assignNewId() {
    myAssignNewId = true;
    return this;
  }

  @SuppressWarnings("UnusedDeclaration")
  @Deprecated
  /**
   * to remove in IDEA 15
   */
  public ExecutionEnvironmentBuilder setDataContext(@Nullable DataContext dataContext) {
    return dataContext(dataContext);
  }

  public ExecutionEnvironmentBuilder dataContext(@Nullable DataContext dataContext) {
    myDataContext = dataContext;
    return this;
  }

  @SuppressWarnings("UnusedDeclaration")
  @Deprecated
  /**
   * to remove in IDEA 15
   */
  public ExecutionEnvironmentBuilder setExecutor(@NotNull Executor executor) {
    return executor(executor);
  }

  public ExecutionEnvironmentBuilder executor(@NotNull Executor executor) {
    myExecutor = executor;
    return this;
  }

  @NotNull
  public ExecutionEnvironment build() {
    if (myRunner == null && myRunnerId == null) {
      myRunner = RunnerRegistry.getInstance().getRunner(myExecutor.getId(), myRunProfile);
    }

    ExecutionEnvironment environment = new ExecutionEnvironment(myRunProfile, myExecutor, myTarget, myProject, myRunnerSettings, myConfigurationSettings, myContentToReuse,
                                                                myRunnerAndConfigurationSettings, myRunnerId, myRunner);
    if (myAssignNewId) {
      environment.assignNewExecutionId();
    }
    if (myDataContext != null) {
      environment.setDataContext(myDataContext);
    }
    return environment;
  }
}
