// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.impl;

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.actionSystem.DataKey;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public interface RunConfigurationCreator {
  DataKey<RunConfigurationCreator> KEY = DataKey.create("RunConfigurationCreator");
  SingleConfigurationConfigurable<RunConfiguration> createNewConfiguration(@NotNull ConfigurationFactory factory);
}
