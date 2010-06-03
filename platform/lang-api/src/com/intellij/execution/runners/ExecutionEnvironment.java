/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationPerRunnerSettings;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/**
 * @author spleaner
 */
public class ExecutionEnvironment {
  private final Project myProject;
  private final RunContentDescriptor myContentToReuse;
  private RunProfile myRunProfile;
  private RunnerSettings myRunnerSettings;
  private ConfigurationPerRunnerSettings myConfigurationSettings;

  @TestOnly
  public ExecutionEnvironment() {
    myProject = null;
    myContentToReuse = null;
  }

  public ExecutionEnvironment(@NotNull final ProgramRunner runner,
                              @NotNull final RunnerAndConfigurationSettings configuration,
                              Project project) {
    this(configuration.getConfiguration(), project, configuration.getRunnerSettings(runner), configuration.getConfigurationSettings(runner),
         null);
  }

  public ExecutionEnvironment(@NotNull RunProfile runProfile,
                              Project project,
                              RunnerSettings runnerSettings,
                              ConfigurationPerRunnerSettings configurationSettings, @Nullable RunContentDescriptor contentToReuse) {
    myRunProfile = runProfile;
    myRunnerSettings = runnerSettings;
    myConfigurationSettings = configurationSettings;
    myProject = project;
    myContentToReuse = contentToReuse;
  }

  /**
   * @deprecated use {@link #ExecutionEnvironment(ProgramRunner, com.intellij.execution.RunnerAndConfigurationSettings, com.intellij.openapi.project.Project)}
   */
  @Deprecated
  public ExecutionEnvironment(@NotNull final ProgramRunner runner, @NotNull final RunnerAndConfigurationSettings configuration, final DataContext context) {
    this(configuration.getConfiguration(), PlatformDataKeys.PROJECT.getData(context), configuration.getRunnerSettings(runner), configuration.getConfigurationSettings(runner), null);
  }

  /**
   * @deprecated use {@link #ExecutionEnvironment(com.intellij.execution.configurations.RunProfile, com.intellij.openapi.project.Project, com.intellij.execution.configurations.RunnerSettings, com.intellij.execution.configurations.ConfigurationPerRunnerSettings, com.intellij.execution.ui.RunContentDescriptor)}
   */
  @Deprecated
  public ExecutionEnvironment(@NotNull final RunProfile profile, final DataContext dataContext) {
    this(profile, PlatformDataKeys.PROJECT.getData(dataContext), null, null, null);
  }

  /**
   * @deprecated use {@link #ExecutionEnvironment(com.intellij.execution.configurations.RunProfile, com.intellij.openapi.project.Project, com.intellij.execution.configurations.RunnerSettings, com.intellij.execution.configurations.ConfigurationPerRunnerSettings, com.intellij.execution.ui.RunContentDescriptor)}
   */
  @Deprecated
  public ExecutionEnvironment(@NotNull final RunProfile runProfile,
                              final RunnerSettings runnerSettings,
                              final ConfigurationPerRunnerSettings configurationSettings,
                              final DataContext dataContext) {
    this(runProfile, PlatformDataKeys.PROJECT.getData(dataContext), runnerSettings, configurationSettings, null);
  }

  @NotNull
  public RunProfile getRunProfile() {
    return myRunProfile;
  }

  @Nullable
  public Project getProject() {
    return myProject;
  }

  /**
   * @deprecated use {@link #getProject()} and {@link #getContentToReuse()}
   */
  @Deprecated
  public DataContext getDataContext() {
    return new DataContext() {
      public Object getData(@NonNls String dataId) {
        return PlatformDataKeys.PROJECT.is(dataId) ? myProject : null;
      }
    };
  }

  @Nullable
  public RunContentDescriptor getContentToReuse() {
    return myContentToReuse;
  }

  @Nullable
  public RunnerSettings getRunnerSettings() {
    return myRunnerSettings;
  }

  public ConfigurationPerRunnerSettings getConfigurationSettings() {
    return myConfigurationSettings;
  }

  @Nullable public RunProfileState getState(final Executor executor) throws ExecutionException {
    return myRunProfile.getState(executor, this);
  }
}
