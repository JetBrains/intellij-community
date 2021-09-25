// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.execution.configuration

import com.intellij.execution.Executor
import com.intellij.execution.configuration.RunConfigurationExtensionBase
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.configurations.SimpleJavaParameters
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration

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

  open fun updateVMParameters(
    configuration: ExternalSystemRunConfiguration,
    javaParameters: SimpleJavaParameters,
    runnerSettings: RunnerSettings?,
    executor: Executor) {
  }
}