// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.runAnything.activity;

import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.openapi.actionSystem.DataContext;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * Implement this class if a particular run configuration should be created for matching input string.
 */
public abstract class RunAnythingMatchedRunConfigurationProvider extends RunAnythingRunConfigurationProvider {
  /**
   * Actual run configuration creation by {@code commandLine}
   *
   * @return created run configuration
   */
  public abstract @NotNull RunnerAndConfigurationSettings createConfiguration(@NotNull DataContext dataContext, @NotNull String pattern);

  /**
   * Returns current provider associated run configuration factory
   */
  public abstract @NotNull ConfigurationFactory getConfigurationFactory();

  @Override
  public Icon getHelpIcon() {
    return getConfigurationFactory().getIcon();
  }
}