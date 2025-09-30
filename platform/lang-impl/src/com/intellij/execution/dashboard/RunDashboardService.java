// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.dashboard;

import com.intellij.execution.RunContentDescriptorId;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.ui.RunContentDescriptor;
import kotlinx.coroutines.CoroutineScope;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public interface RunDashboardService extends RunDashboardRunConfigurationNode {
  @NotNull
  RunDashboardServiceId getUuid();
  CoroutineScope getScope();

  default String getServiceViewId() {
    var configuration = getConfigurationSettings().getConfiguration();
    return configuration.getType().getId() + "/" + configuration.getName();
  }

  @Override
  @NotNull
  RunnerAndConfigurationSettings getConfigurationSettings();

  @Nullable
  RunContentDescriptorId getDescriptorId();

  @Override
  @Nullable
  RunContentDescriptor getDescriptor();
}
