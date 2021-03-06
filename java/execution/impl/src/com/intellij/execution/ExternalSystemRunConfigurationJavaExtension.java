// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution;

import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.execution.configurations.SimpleJavaParameters;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemProcessHandler;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfigurationExtension;
import com.intellij.openapi.options.SettingsEditorGroup;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Experimental
final class ExternalSystemRunConfigurationJavaExtension implements ExternalSystemRunConfigurationExtension {
  @Override
  public void readExternal(@NotNull ExternalSystemRunConfiguration configuration, @NotNull Element element) {
    JavaRunConfigurationExtensionManager javaRunConfigurationExtensionManager = JavaRunConfigurationExtensionManager.getInstanceOrNull();
    if (javaRunConfigurationExtensionManager != null) {
      javaRunConfigurationExtensionManager.readExternal(configuration, element);
    }
  }

  @Override
  public void writeExternal(@NotNull ExternalSystemRunConfiguration configuration, @NotNull Element element) {
    JavaRunConfigurationExtensionManager javaRunConfigurationExtensionManager = JavaRunConfigurationExtensionManager.getInstanceOrNull();
    if (javaRunConfigurationExtensionManager != null) {
      javaRunConfigurationExtensionManager.writeExternal(configuration, element);
    }
  }

  @Override
  public void appendEditors(@NotNull ExternalSystemRunConfiguration configuration,
                            @NotNull SettingsEditorGroup<ExternalSystemRunConfiguration> group) {
    JavaRunConfigurationExtensionManager javaRunConfigurationExtensionManager = JavaRunConfigurationExtensionManager.getInstanceOrNull();
    if (javaRunConfigurationExtensionManager != null) {
      javaRunConfigurationExtensionManager.appendEditors(configuration, group);
    }
  }

  @Override
  public void attachToProcess(@NotNull ExternalSystemRunConfiguration configuration,
                              @NotNull ExternalSystemProcessHandler processHandler,
                              @Nullable RunnerSettings settings) {
    JavaRunConfigurationExtensionManager javaRunConfigurationExtensionManager = JavaRunConfigurationExtensionManager.getInstanceOrNull();
    if (javaRunConfigurationExtensionManager != null) {
      javaRunConfigurationExtensionManager.attachExtensionsToProcess(configuration, processHandler, settings);
    }
  }

  @Override
  public void updateVMParameters(@NotNull ExternalSystemRunConfiguration configuration,
                                 @NotNull SimpleJavaParameters javaParameters,
                                 @Nullable RunnerSettings settings,
                                 @NotNull Executor executor) throws ExecutionException {
    final JavaParameters extensionsJP = new JavaParameters();
    JavaRunConfigurationExtensionManager javaRunConfigurationExtensionManager = JavaRunConfigurationExtensionManager.getInstanceOrNull();
    if (javaRunConfigurationExtensionManager != null) {
      javaRunConfigurationExtensionManager.updateJavaParameters(configuration, extensionsJP, settings, executor);
    }
    extensionsJP.getVMParametersList().copyTo(javaParameters.getVMParametersList());
  }
}
