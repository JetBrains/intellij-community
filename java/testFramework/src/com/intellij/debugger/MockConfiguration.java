// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger;

import com.intellij.execution.Executor;
import com.intellij.execution.configurations.ModuleRunConfiguration;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.configurations.RunConfigurationOptions;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.configurations.UnknownConfigurationType;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class MockConfiguration extends RunConfigurationBase<RunConfigurationOptions> implements ModuleRunConfiguration {
  private final Module myModule;

  public MockConfiguration(Project project, Module module) {
    super(project, UnknownConfigurationType.getInstance(), "");
    this.myModule = module;
  }

  @Override
  public Module @NotNull [] getModules() {
    return myModule == null ? Module.EMPTY_ARRAY : new Module[]{myModule};
  }

  @Override
  public @NotNull SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
    throw new UnsupportedOperationException();
  }

  @Override
  public RunProfileState getState(@NotNull Executor executor, @NotNull ExecutionEnvironment env) {
    return null;
  }
}
