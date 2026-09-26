// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.project

import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager
import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManagerFactory
import com.intellij.openapi.module.Module

internal class ExternalSystemModulePropertyManagerFactoryImpl : ExternalSystemModulePropertyManagerFactory {

  override fun getService(module: Module): ExternalSystemModulePropertyManager {
    return ExternalSystemModulePropertyManagerBridge(module)
  }
}
