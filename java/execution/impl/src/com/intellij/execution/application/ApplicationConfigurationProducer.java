// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.application;

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NotNull;

public class ApplicationConfigurationProducer extends AbstractApplicationConfigurationProducer<ApplicationConfiguration>
  implements DumbAware {

  @Override
  public @NotNull ConfigurationFactory getConfigurationFactory() {
    return ApplicationConfigurationType.getInstance().getConfigurationFactories()[0];
  }
}
