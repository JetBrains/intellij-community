// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.application;

import com.intellij.execution.configuration.ConfigurationFactoryListener;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.ModuleBasedConfiguration;
import com.intellij.openapi.components.BaseState;
import org.jetbrains.annotations.NotNull;

public abstract class JvmMainMethodConfigurationFactoryBase extends ConfigurationFactory implements ConfigurationFactoryListener<ModuleBasedConfiguration> {
  protected JvmMainMethodConfigurationFactoryBase(@NotNull ConfigurationType type) {
    super(type);
  }

  @Override
  public void onNewConfigurationCreated(@NotNull ModuleBasedConfiguration configuration) {
    configuration.onNewConfigurationCreated();
  }

  @Override
  public Class<? extends BaseState> getOptionsClass() {
    return ApplicationConfigurationOptions.class;
  }
}
