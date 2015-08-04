/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.execution.configurations.*;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.options.SettingsEditor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

abstract class BaseProgramRunner<Settings extends RunnerSettings> implements ProgramRunner<Settings> {
  @Override
  @Nullable
  public Settings createConfigurationData(ConfigurationInfoProvider settingsProvider) {
    return null;
  }

  @Override
  public void checkConfiguration(RunnerSettings settings, ConfigurationPerRunnerSettings configurationPerRunnerSettings)
    throws RuntimeConfigurationException {
  }

  @Override
  public void onProcessStarted(RunnerSettings settings, ExecutionResult executionResult) {
  }

  @Override
  @Nullable
  public SettingsEditor<Settings> getSettingsEditor(Executor executor, RunConfiguration configuration) {
    return null;
  }

  @Override
  public void execute(@NotNull ExecutionEnvironment environment) throws ExecutionException {
    execute(environment, null);
  }

  @Override
  public void execute(@NotNull ExecutionEnvironment environment, @Nullable Callback callback) throws ExecutionException {
    RunProfileState state = environment.getState();
    if (state == null) {
      return;
    }

    RunManager.getInstance(environment.getProject()).refreshUsagesList(environment.getRunProfile());
    execute(environment, callback, state);
  }

  protected abstract void execute(@NotNull ExecutionEnvironment environment,
                                  @Nullable Callback callback,
                                  @NotNull RunProfileState state) throws ExecutionException;

  @Nullable
  static RunContentDescriptor postProcess(@NotNull ExecutionEnvironment environment, @Nullable RunContentDescriptor descriptor, @Nullable Callback callback) {
    if (descriptor != null) {
      descriptor.setExecutionId(environment.getExecutionId());
      RunnerAndConfigurationSettings settings = environment.getRunnerAndConfigurationSettings();
      if (settings != null) {
        descriptor.setActivateToolWindowWhenAdded(settings.isActivateToolWindowBeforeRun());
      }
    }
    if (callback != null) {
      callback.processStarted(descriptor);
    }
    return descriptor;
  }
}