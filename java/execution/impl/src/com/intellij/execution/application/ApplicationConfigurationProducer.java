// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.application;

import com.intellij.execution.configurations.ConfigurationFactory;
import org.jetbrains.annotations.NotNull;

public class ApplicationConfigurationProducer extends AbstractApplicationConfigurationProducer<ApplicationConfiguration> {
  @NotNull
  @Override
  public ConfigurationFactory getConfigurationFactory() {
    return ApplicationConfigurationType.getInstance().getConfigurationFactories()[0];
  }
}
