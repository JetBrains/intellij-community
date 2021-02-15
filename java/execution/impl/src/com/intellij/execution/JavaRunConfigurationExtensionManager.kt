// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution

import com.intellij.execution.configuration.RunConfigurationExtensionsManager
import com.intellij.execution.configurations.JavaParameters
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceOrNull
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.runAndLogException

@Service
class JavaRunConfigurationExtensionManager : RunConfigurationExtensionsManager<RunConfigurationBase<*>, RunConfigurationExtension>(RunConfigurationExtension.EP_NAME) {
  companion object {
    private val LOG = logger<RunConfigurationExtension>()
    @JvmStatic
    val instance: JavaRunConfigurationExtensionManager
      get() = service()

    @JvmStatic
    val instanceOrNull: JavaRunConfigurationExtensionManager?
      get() = serviceOrNull()

    @JvmStatic
    fun checkConfigurationIsValid(configuration: RunConfigurationBase<*>) {
      LOG.runAndLogException {
        instance.validateConfiguration(configuration, false)
      }
    }
  }

  @Throws(ExecutionException::class)
  fun <T : RunConfigurationBase<*>> updateJavaParameters(configuration: T,
                                                         params: JavaParameters,
                                                         runnerSettings: RunnerSettings?,
                                                         executor: Executor) {
    // only for enabled extensions
    processEnabledExtensions(configuration, runnerSettings) {
      it.updateJavaParameters(configuration, params, runnerSettings, executor)
    }
  }

  override val idAttrName = "name"

  override val extensionRootAttr = "extension"
}
