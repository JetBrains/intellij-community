// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project

class DefaultEnvironmentParametersService : BaseEnvironmentParametersService() {

  override fun getEnvironmentValue(project: Project?, key: EnvironmentKey): String? {
    if (ApplicationManager.getApplication().isHeadlessEnvironment) {
      LOG.warn("Access to UI is not allowed in the headless environment")
    }
    checkKeyRegistered(key)
    return null
  }



}