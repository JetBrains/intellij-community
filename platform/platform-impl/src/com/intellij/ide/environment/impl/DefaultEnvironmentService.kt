// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.environment.impl

import com.intellij.ide.environment.EnvironmentKey
import com.intellij.openapi.application.ApplicationManager

class DefaultEnvironmentService : BaseEnvironmentService() {

  override suspend fun getEnvironmentValue(key: EnvironmentKey): String? {
    if (ApplicationManager.getApplication().isHeadlessEnvironment) {
      LOG.warn("Access to UI is not allowed in the headless environment")
    }
    checkKeyRegistered(key)
    return null
  }

  override suspend fun getEnvironmentValue(key: EnvironmentKey, defaultValue: String): String {
    getEnvironmentValue(key)
    return defaultValue
  }
}
