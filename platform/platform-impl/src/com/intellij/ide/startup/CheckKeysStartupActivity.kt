// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup

import com.intellij.ide.environment.EnvironmentKeyRegistry
import com.intellij.ide.environment.EnvironmentParametersService
import com.intellij.ide.environment.impl.HeadlessEnvironmentParametersService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

class CheckKeysStartupActivity : ProjectActivity {
  override suspend fun execute(project: Project) {
    if (!ApplicationManager.getApplication().isHeadlessEnvironment) {
      return
    }

    val environmentService = blockingContext { service<EnvironmentParametersService>() }
    val messageBuilder = StringBuilder()
    var exceptionOccurred = false
    for (registry in blockingContext { EnvironmentKeyRegistry.EP_NAME.extensionList }) {
      for (requiredKey in registry.getRequiredKeys(project)) {
        val value = environmentService.getEnvironmentValueOrNull(requiredKey)
        if (value == null) {
          exceptionOccurred = true
          messageBuilder.appendLine(HeadlessEnvironmentParametersService.MissingEnvironmentKeyException(requiredKey).message)
        }
      }
    }
    if (exceptionOccurred) {
      thisLogger().error(messageBuilder.toString())
    }
  }
}