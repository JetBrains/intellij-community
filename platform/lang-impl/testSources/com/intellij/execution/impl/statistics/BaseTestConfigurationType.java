// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.impl.statistics;

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public abstract class BaseTestConfigurationType implements ConfigurationType {

  @NotNull
  @Override
  public String getDisplayName() {
    return getId();
  }

  @Override
  public String getConfigurationTypeDescription() {
    return getId();
  }

  @Override
  public Icon getIcon() {
    return null;
  }

  @Override
  public ConfigurationFactory[] getConfigurationFactories() {
    return new ConfigurationFactory[0];
  }

  protected static class FirstTestRunConfigurationType extends BaseTestConfigurationType {
    @Override
    @NotNull
    public String getId() {
      return FirstTestRunConfigurationType.class.getSimpleName();
    }
  }

  protected static class SecondTestRunConfigurationType extends BaseTestConfigurationType {
    @Override
    @NotNull
    public String getId() {
      return SecondTestRunConfigurationType.class.getSimpleName();
    }
  }
}
