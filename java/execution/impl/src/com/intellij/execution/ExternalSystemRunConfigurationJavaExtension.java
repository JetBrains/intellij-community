// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution;

import com.intellij.execution.configurations.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemProcessHandler;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfigurationExtension;
import com.intellij.openapi.options.SettingsEditorGroup;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Experimental
public class ExternalSystemRunConfigurationJavaExtension implements ExternalSystemRunConfigurationExtension {
  private static final Logger LOG = Logger.getInstance(ExternalSystemRunConfigurationJavaExtension.class);

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
                                 @NotNull Executor executor) {
    final JavaParameters extensionsJP = new JavaParameters();
    for (RunConfigurationExtension ext : RunConfigurationExtension.EP_NAME.getExtensionList()) {
      try {
        ext.updateJavaParameters(configuration, extensionsJP, settings, executor);
      }
      catch (ExecutionException e) {
        LOG.error(e);
      }
    }
    copy(extensionsJP.getVMParametersList(), javaParameters.getVMParametersList());
  }

  private static void copy(@NotNull ParametersList from, @NotNull ParametersList to) {
    to.addAll(from.getParameters());
    for (ParamsGroup group : from.getParamsGroups()) {
      to.addParamsGroup(group);
    }
  }
}
