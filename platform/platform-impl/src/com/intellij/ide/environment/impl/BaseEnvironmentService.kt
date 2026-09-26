// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.environment.impl

import com.intellij.ide.environment.EnvironmentKey
import com.intellij.ide.environment.EnvironmentKeyProvider
import com.intellij.ide.environment.EnvironmentService
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger

abstract class BaseEnvironmentService : EnvironmentService {
  companion object {
    @JvmStatic
    protected val LOG: Logger = logger<EnvironmentService>()
  }

  protected fun checkKeyRegistered(key: EnvironmentKey) {
    val isKeyRegistered = EnvironmentKeyProvider.EP_NAME.extensionList.any { provider ->
      provider.knownKeys.any { it.key == key }
    }
    if (!isKeyRegistered) {
      LOG.warn("The key '${key.id}' is not registered in any 'com.intellij.ide.environment.EnvironmentKeyRegistry'. " +
               "It may lead to poor discoverability of this key and to worsened support from the IDE.")
    }
  }
}