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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/**
 * @author spleaner
 */
public class ExecutionEnvironment {
  private DataContext myDataContext;
  private RunProfile myRunProfile;
  private RunnerSettings myRunnerSettings;
  private ConfigurationPerRunnerSettings myConfigurationSettings;

  @TestOnly
  public ExecutionEnvironment() {
  }

  public ExecutionEnvironment(@NotNull final ProgramRunner runner, @NotNull final RunnerAndConfigurationSettings configuration, final DataContext context) {
    this(configuration.getConfiguration(), configuration.getRunnerSettings(runner), configuration.getConfigurationSettings(runner), context);
  }

  public ExecutionEnvironment(@NotNull final RunProfile profile, final DataContext dataContext) {
    myRunProfile = profile;
    myDataContext = dataContext;
  }

  public ExecutionEnvironment(@NotNull final RunProfile runProfile,
                              final RunnerSettings runnerSettings,
                              final ConfigurationPerRunnerSettings configurationSettings,
                              final DataContext dataContext) {
    this(runProfile, dataContext);
    myRunnerSettings = runnerSettings;
    myConfigurationSettings = configurationSettings;
  }

  @NotNull
  public RunProfile getRunProfile() {
    return myRunProfile;
  }

  @Nullable
  public Project getProject() {
    return PlatformDataKeys.PROJECT.getData(myDataContext);
  }

  @Deprecated
  public DataContext getDataContext() {
    return myDataContext;
  }

  @Nullable
  public RunContentDescriptor getContentToReuse() {
    return GenericProgramRunner.CONTENT_TO_REUSE_DATA_KEY.getData(myDataContext);
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
