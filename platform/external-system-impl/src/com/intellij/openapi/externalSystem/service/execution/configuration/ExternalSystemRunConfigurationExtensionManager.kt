// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.execution.configuration

import com.intellij.execution.Executor
import com.intellij.execution.configuration.RunConfigurationExtensionsManager
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.configurations.SimpleJavaParameters
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Service(Service.Level.APP)
class ExternalSystemRunConfigurationExtensionManager
  : RunConfigurationExtensionsManager<ExternalSystemRunConfiguration, ExternalSystemRunConfigurationExtension>(EP_NAME) {

  fun updateVMParameters(
    configuration: ExternalSystemRunConfiguration,
    javaParameters: SimpleJavaParameters,
    runnerSettings: RunnerSettings?,
    executor: Executor
  ) {
    processEnabledExtensions(configuration, runnerSettings) {
      it.updateVMParameters(configuration, javaParameters, runnerSettings, executor)
    }
  }

  companion object {
    private val EP_NAME = ExtensionPointName<ExternalSystemRunConfigurationExtension>("com.intellij.externalSystem.runConfigurationEx")

    @JvmStatic
    fun getInstance(): ExternalSystemRunConfigurationExtensionManager = service()
  }
}