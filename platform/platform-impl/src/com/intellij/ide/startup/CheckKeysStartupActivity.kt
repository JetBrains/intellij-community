// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup

import com.intellij.ide.environment.EnvironmentKeyProvider
import com.intellij.ide.environment.EnvironmentService
import com.intellij.ide.environment.impl.HeadlessEnvironmentService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

private const val UNDEFINED = "!!!___***undefined***___!!!"

private class CheckKeysStartupActivity : ProjectActivity {
  init {
    val app = ApplicationManager.getApplication()
    if (app.isUnitTestMode || !app.isHeadlessEnvironment) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override suspend fun execute(project: Project) {
    delay(5.seconds)

    val environmentService = serviceAsync<EnvironmentService>()
    val messageBuilder = StringBuilder()
    var exceptionOccurred = false
    for (registry in EnvironmentKeyProvider.EP_NAME.extensionList) {
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
}
