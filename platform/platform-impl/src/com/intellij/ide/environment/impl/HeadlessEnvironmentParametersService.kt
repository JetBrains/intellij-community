// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.environment.impl

import com.intellij.ide.environment.EnvironmentKey
import com.intellij.openapi.application.ApplicationManager
import kotlinx.coroutines.CancellationException

class HeadlessEnvironmentParametersService : BaseEnvironmentParametersService() {

  override suspend fun getEnvironmentValue(key: EnvironmentKey): String {
    if (!ApplicationManager.getApplication().isHeadlessEnvironment) {
      LOG.warn("Access to environment parameters in the IDE with UI must be delegated to the user")
    }
    checkKeyRegistered(key)
    val property = System.getProperty(key.id) ?: key.defaultValue.ifEmpty { null }
    if (property == null) {
      throw CancellationException(
        """Missing key: ${key.id}
          |Usage:
          |${key.description.get()}
          |""".trimMargin())
    }
    return property
  }
}

