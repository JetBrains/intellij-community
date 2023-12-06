// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.execution;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.execution.configurations.SimpleJavaParameters;
import com.intellij.openapi.options.SettingsEditorGroup;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @deprecated Use {@link com.intellij.openapi.externalSystem.service.execution.configuration.ExternalSystemRunConfigurationExtension}
 * and {@code com.intellij.externalSystem.runConfigurationEx} extension point name.
 */
@Deprecated
public interface ExternalSystemRunConfigurationExtension {
  void readExternal(@NotNull ExternalSystemRunConfiguration configuration, @NotNull Element element);

  void writeExternal(@NotNull ExternalSystemRunConfiguration configuration, @NotNull Element element);

  void appendEditors(@NotNull ExternalSystemRunConfiguration configuration,
                     @NotNull SettingsEditorGroup<ExternalSystemRunConfiguration> group);

  void attachToProcess(@NotNull ExternalSystemRunConfiguration configuration,
                       @NotNull ExternalSystemProcessHandler processHandler,
                       @Nullable RunnerSettings settings);

  void updateVMParameters(@NotNull ExternalSystemRunConfiguration configuration,
                          @NotNull SimpleJavaParameters javaParameters,
                          @Nullable RunnerSettings settings,
                          @NotNull Executor executor) throws ExecutionException;
}
