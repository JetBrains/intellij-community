// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.JavaParameters
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.configurations.SimpleJavaParameters
import com.intellij.execution.process.ProcessHandler
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration
import com.intellij.openapi.externalSystem.service.execution.configuration.ExternalSystemRunConfigurationExtension
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.options.SettingsEditorGroup
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus
import com.intellij.execution.JavaRunConfigurationExtensionManager.Companion.instance as javaExtensionManager

@ApiStatus.Experimental
internal class ExternalSystemRunConfigurationJavaExtension : ExternalSystemRunConfigurationExtension() {
  override fun patchCommandLine(
    configuration: ExternalSystemRunConfiguration,
    runnerSettings: RunnerSettings?,
    cmdLine: GeneralCommandLine,
    runnerId: String
  ) {
    javaExtensionManager.patchCommandLine(configuration, runnerSettings, cmdLine, runnerId)
  }

  override fun readExternal(configuration: ExternalSystemRunConfiguration, element: Element) {
    javaExtensionManager.readExternal(configuration, element)
  }

  override fun writeExternal(configuration: ExternalSystemRunConfiguration, element: Element) {
    javaExtensionManager.writeExternal(configuration, element)
  }

  fun appendEditors(
    configuration: ExternalSystemRunConfiguration,
    group: SettingsEditorGroup<ExternalSystemRunConfiguration>
  ) {
    javaExtensionManager.appendEditors(configuration, group)
  }

  override fun <P : ExternalSystemRunConfiguration> createFragments(configuration: P): List<SettingsEditor<P>> {
    return javaExtensionManager.createFragments(configuration)
  }

  override fun attachToProcess(
    configuration: ExternalSystemRunConfiguration,
    processHandler: ProcessHandler,
    settings: RunnerSettings?
  ) {
    javaExtensionManager.attachExtensionsToProcess(configuration, processHandler, settings)
  }

  override fun updateVMParameters(
    configuration: ExternalSystemRunConfiguration,
    javaParameters: SimpleJavaParameters,
    runnerSettings: RunnerSettings?,
    executor: Executor
  ) {
    val extensionsJP = JavaParameters()
    javaExtensionManager.updateJavaParameters(configuration, extensionsJP, runnerSettings, executor)
    extensionsJP.vmParametersList.copyTo(javaParameters.vmParametersList)
  }
}
