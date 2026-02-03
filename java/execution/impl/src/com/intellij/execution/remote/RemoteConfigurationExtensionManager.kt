// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.remote

import com.intellij.execution.RunConfigurationExtension
import com.intellij.execution.configuration.RunConfigurationExtensionsManager
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service

@Service
internal class RemoteConfigurationExtensionManager
  : RunConfigurationExtensionsManager<RunConfigurationBase<*>, RunConfigurationExtension>(RunConfigurationExtension.EP_NAME) {

  companion object {
    fun getInstance(): RemoteConfigurationExtensionManager = service()
  }
}