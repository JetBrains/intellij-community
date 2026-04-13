// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.configurations;

import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public interface OngoingRunConfigurationProvider {
  ExtensionPointName<OngoingRunConfigurationProvider> EP_NAME =
    new ExtensionPointName<>("com.intellij.execution.ongoingRunConfigurationProvider");

  boolean hasRunConfigurationRunning(@NotNull Project project, @NotNull RunnerAndConfigurationSettings settings);
}
