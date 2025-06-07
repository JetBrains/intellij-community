// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger;

import com.intellij.execution.Executor;
import com.intellij.execution.configurations.*;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class MockConfiguration implements ModuleRunConfiguration {
  private final Project project;
  private final Module myModule;

  public MockConfiguration(Project project, Module module) {
    this.project = project;
    this.myModule = module;
  }

  @Override
  public Module @NotNull [] getModules() {
    return myModule == null ? Module.EMPTY_ARRAY : new Module[]{myModule};
  }

  @Override
  public Icon getIcon() {
    return null;
  }

  @Override
  public ConfigurationFactory getFactory() {
    return UnknownConfigurationType.getInstance();
  }

  @Override
  public void setName(@NotNull String name) { }

  @Override
  public @NotNull SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
    throw new UnsupportedOperationException();
  }

  @Override
  public Project getProject() {
    return project;
  }

  @Override
  public RunConfiguration clone() {
    return null;
  }

  @Override
  public RunProfileState getState(@NotNull Executor executor, @NotNull ExecutionEnvironment env) {
    return null;
  }

  @Override
  public @NotNull String getName() {
    return "";
  }
}
