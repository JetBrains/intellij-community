// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.environment.impl

import com.intellij.ide.environment.EnvironmentKey
import com.intellij.openapi.application.ApplicationManager

class DefaultEnvironmentParametersService : BaseEnvironmentParametersService() {

  override suspend fun requestEnvironmentValue(key: EnvironmentKey): String? {
    if (ApplicationManager.getApplication().isHeadlessEnvironment) {
      LOG.warn("Access to UI is not allowed in the headless environment")
    }
    checkKeyRegistered(key)
    return key.defaultValue.ifEmpty { null }
  }

  override suspend fun getEnvironmentValueOrNull(key: EnvironmentKey): String? = requestEnvironmentValue(key)

}