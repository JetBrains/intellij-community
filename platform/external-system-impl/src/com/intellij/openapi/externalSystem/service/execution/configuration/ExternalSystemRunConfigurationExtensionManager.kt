// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.execution.configuration

import com.intellij.execution.Executor
import com.intellij.execution.configuration.RunConfigurationExtensionsManager
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.configurations.SimpleJavaParameters
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemProcessHandler
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration
import com.intellij.openapi.options.SettingsEditorGroup
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfigurationExtension as DeprecatedExternalSystemRunConfigurationExtension

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

    private val DEPRECATED_EP_NAME =
      ExtensionPointName<DeprecatedExternalSystemRunConfigurationExtension>("com.intellij.externalSystem.runConfigurationExtension")

    fun getInstance(): ExternalSystemRunConfigurationExtensionManager = service()

    @ApiStatus.Internal
    @JvmStatic
    fun readExternal(configuration: ExternalSystemRunConfiguration, element: Element) {
      DEPRECATED_EP_NAME.forEachExtensionSafe { it.readExternal(configuration, element) }
      getInstance().readExternal(configuration, element)
    }

    @ApiStatus.Internal
    @JvmStatic
    fun writeExternal(configuration: ExternalSystemRunConfiguration, element: Element) {
      DEPRECATED_EP_NAME.forEachExtensionSafe { it.writeExternal(configuration, element) }
      getInstance().writeExternal(configuration, element)
    }

    @ApiStatus.Internal
    @JvmStatic
    fun appendEditors(
      configuration: ExternalSystemRunConfiguration,
      group: SettingsEditorGroup<ExternalSystemRunConfiguration>
    ) {
      DEPRECATED_EP_NAME.forEachExtensionSafe { it.appendEditors(configuration, group) }
      getInstance().appendEditors(configuration, group)
    }

    @ApiStatus.Internal
    @JvmStatic
    fun attachToProcess(
      configuration: ExternalSystemRunConfiguration,
      processHandler: ExternalSystemProcessHandler,
      runnerSettings: RunnerSettings?
    ) {
      DEPRECATED_EP_NAME.forEachExtensionSafe { it.attachToProcess(configuration, processHandler, runnerSettings) }
      getInstance().attachExtensionsToProcess(configuration, processHandler, runnerSettings)
    }

    @ApiStatus.Internal
    @JvmStatic
    fun createVMParameters(
      configuration: ExternalSystemRunConfiguration,
      runnerSettings: RunnerSettings?,
      executor: Executor
    ): SimpleJavaParameters {
      val javaParameters = SimpleJavaParameters()
      DEPRECATED_EP_NAME.forEachExtensionSafe { it.updateVMParameters(configuration, javaParameters, runnerSettings, executor) }
      getInstance().updateVMParameters(configuration, javaParameters, runnerSettings, executor)
      return javaParameters
    }
  }
}