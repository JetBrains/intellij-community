// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.actions;

import com.intellij.execution.Executor;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.RunConfiguration;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class RunNewConfigurationContextAction extends RunContextAction {
  public RunNewConfigurationContextAction(@NotNull Executor executor) {
    super(executor);
  }

  @Override
  protected @Nullable RunnerAndConfigurationSettings findExisting(@NotNull ConfigurationContext context) {
    return null;
  }

  @Override
  protected boolean isEnabledFor(@NotNull RunConfiguration configuration, @NotNull ConfigurationContext context) {
    return super.isEnabledFor(configuration) && isNewConfiguration(configuration, context);
  }

  /**
   * @return true if there is existing configuration in this context but it is not {@code configuration}
   */
  public static boolean isNewConfiguration(RunConfiguration configuration, ConfigurationContext context) {
    RunnerAndConfigurationSettings existing = context.findExisting();
    return existing != null && !existing.getConfiguration().equals(configuration);
  }
}
