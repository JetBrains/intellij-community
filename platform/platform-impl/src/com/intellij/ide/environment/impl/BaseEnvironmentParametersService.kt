// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.environment.impl

import com.intellij.ide.environment.EnvironmentKey
import com.intellij.ide.environment.EnvironmentKeyRegistry
import com.intellij.ide.environment.EnvironmentParametersService
import com.intellij.openapi.diagnostic.logger

abstract class BaseEnvironmentParametersService : EnvironmentParametersService {
  companion object {
    @JvmStatic
    protected val LOG = logger<EnvironmentParametersService>()
  }

  protected fun checkKeyRegistered(key: EnvironmentKey) {
    val isKeyRegistered = EnvironmentKeyRegistry.EP_NAME.extensionList.any { it.getAllKeys().contains(key) }
    if (!isKeyRegistered) {
      LOG.warn("The key '${key.id}' is not registered in any 'com.intellij.ide.environment.EnvironmentKeyRegistry'. " +
               "It may lead to poor discoverability of this key and to worsened support from the IDE.")
    }
  }
}