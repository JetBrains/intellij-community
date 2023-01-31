// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project

class HeadlessEnvironmentParametersService : BaseEnvironmentParametersService() {

  override fun getEnvironmentValue(project: Project?, key: EnvironmentKey): String {
    if (!ApplicationManager.getApplication().isHeadlessEnvironment) {
      LOG.warn("Access to environment parameters in the IDE with UI must be delegated to the user")
    }
    checkKeyRegistered(key)
    val property = System.getProperty(key.getId())
    if (property == null) {
      throw EnvironmentParametersService.MissingEnvironmentKeyException(key)
    }
    return property
  }

}

