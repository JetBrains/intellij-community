// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.application;

import com.intellij.execution.configuration.ConfigurationFactoryEx;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.ModuleBasedConfiguration;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.components.BaseState;
import org.jetbrains.annotations.NotNull;

public abstract class JvmMainMethodConfigurationFactoryBase extends ConfigurationFactoryEx {
  protected JvmMainMethodConfigurationFactoryBase(@NotNull ConfigurationType type) {
    super(type);
  }

  @Override
  public void onNewConfigurationCreated(@NotNull RunConfiguration configuration) {
    ((ModuleBasedConfiguration)configuration).onNewConfigurationCreated();
  }

  @Override
  public Class<? extends BaseState> getOptionsClass() {
    return ApplicationConfigurationOptions.class;
  }
}
