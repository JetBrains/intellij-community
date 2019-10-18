// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.runners;

import com.intellij.execution.*;
import com.intellij.execution.configurations.*;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.options.SettingsEditor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class BaseProgramRunner<Settings extends RunnerSettings> implements ProgramRunner<Settings> {
  @Override
  @Nullable
  public Settings createConfigurationData(@NotNull ConfigurationInfoProvider settingsProvider) {
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

      String toolWindowId = ExecutionManager.getInstance(environment.getProject()).getContentManager().getContentDescriptorToolWindowId(environment);
      if (toolWindowId != null) {
        descriptor.setContentToolWindowId(toolWindowId);
      }

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