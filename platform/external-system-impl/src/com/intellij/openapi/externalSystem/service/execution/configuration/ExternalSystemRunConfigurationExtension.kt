// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.execution.configuration

import com.intellij.execution.Executor
import com.intellij.execution.configuration.RunConfigurationExtensionBase
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.configurations.SimpleJavaParameters
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration

/**
 * Allows a plugin to extend a [ExternalSystemRunConfiguration] created by another plugin.
 *
 * @see RunConfigurationExtensionBase
 */
abstract class ExternalSystemRunConfigurationExtension : RunConfigurationExtensionBase<ExternalSystemRunConfiguration>() {
  override fun isApplicableFor(configuration: ExternalSystemRunConfiguration): Boolean {
    return true
  }

  override fun isEnabledFor(applicableConfiguration: ExternalSystemRunConfiguration, runnerSettings: RunnerSettings?): Boolean {
    return true
  }

  override fun patchCommandLine(configuration: ExternalSystemRunConfiguration,
                                runnerSettings: RunnerSettings?,
                                cmdLine: GeneralCommandLine,
                                runnerId: String) {
  }

  /**
   * Patches the vm parameters in [javaParameters] of the process about to be started by the underlying run configuration.
   *
   * @param configuration  the underlying run configuration.
   * @param javaParameters the java parameters of the process about to be started.
   * @param runnerSettings the runner-specific settings.
   * @param executor       the executor which is using to run the configuration.
   *
   * @see com.intellij.execution.RunConfigurationExtension.updateJavaParameters from java plugin
   */
  open fun updateVMParameters(
    configuration: ExternalSystemRunConfiguration,
    javaParameters: SimpleJavaParameters,
    runnerSettings: RunnerSettings?,
    executor: Executor) {
  }
}