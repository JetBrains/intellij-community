// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution

import com.intellij.execution.configuration.RunConfigurationExtensionsManager
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceOrNull
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.runAndLogException

private val LOG = logger<RunConfigurationExtension>()

class JavaRunConfigurationExtensionManager : RunConfigurationExtensionsManager<RunConfigurationBase<*>, RunConfigurationExtension>(RunConfigurationExtension.EP_NAME) {
  companion object {
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

  override val idAttrName = "name"

  override val extensionRootAttr = "extension"
}
