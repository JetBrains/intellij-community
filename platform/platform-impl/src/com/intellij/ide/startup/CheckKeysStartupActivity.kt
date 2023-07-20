// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup

import com.intellij.ide.environment.EnvironmentKeyProvider
import com.intellij.ide.environment.EnvironmentService
import com.intellij.ide.environment.impl.HeadlessEnvironmentService
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

    val environmentService = blockingContext { service<EnvironmentService>() }
    val messageBuilder = StringBuilder()
    var exceptionOccurred = false
    for (registry in blockingContext { EnvironmentKeyProvider.EP_NAME.extensionList }) {
      for (requiredKey in registry.getRequiredKeys(project)) {
        val value = environmentService.getEnvironmentValue(requiredKey, UNDEFINED)
        if (value == UNDEFINED) {
          exceptionOccurred = true
          messageBuilder.appendLine(HeadlessEnvironmentService.MissingEnvironmentKeyException(requiredKey).message)
        }
      }
    }
    if (exceptionOccurred) {
      thisLogger().error(messageBuilder.toString())
    }
  }

  companion object {
    private const val UNDEFINED: String = "!!!___***undefined***___!!!"
  }
}
