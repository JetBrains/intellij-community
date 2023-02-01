// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup

import com.intellij.ide.EnvironmentKeyRegistry
import com.intellij.ide.EnvironmentParametersService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectPostStartupActivity
import kotlinx.coroutines.CancellationException

class CheckKeysStartupActivity : ProjectPostStartupActivity {
  override suspend fun execute(project: Project) {
    if (!ApplicationManager.getApplication().isHeadlessEnvironment) {
      return
    }

    val environmentService = blockingContext { service<EnvironmentParametersService>() }
    val messageBuilder = StringBuilder()
    for (registry in blockingContext { EnvironmentKeyRegistry.EP_NAME.extensionList }) {
      for (requiredKey in registry.getRequiredKeys(project)) {
        try {
          environmentService.getEnvironmentValue(project, requiredKey)
        }
        catch (e: CancellationException) {
          messageBuilder.appendLine(e.message)
        }
      }
    }
    thisLogger().error(messageBuilder)
  }
}