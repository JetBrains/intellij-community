// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler.backwardRefs

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.compiler.CompilerManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity

/**
 * Call [IsUpToDateCheckConsumer.isUpToDate] if [CompilerManager.isUpToDate] returns 'true'.
 *
 * @see IsUpToDateCheckConsumer
 */
class IsUpToDateCheckStartupActivity : StartupActivity.Background {
  override fun runActivity(project: Project) {
    if (ApplicationManager.getApplication().isUnitTestMode) return
    val logger = thisLogger()

    val isUpToDateConsumers = IsUpToDateCheckConsumer.EP_NAME.extensionList.filter { it.isApplicable(project) }
    if (isUpToDateConsumers.isEmpty()) {
      logger.info("suitable consumer is not found")
      return
    }
    else {
      logger.info("activity started")
    }

    val compilerManager = CompilerManager.getInstance(project)
    val projectCompileScope = compilerManager.createProjectCompileScope(project)
    val isUpToDate = compilerManager.isUpToDate(projectCompileScope)

    logger.info("isUpToDate = $isUpToDate")
    for (consumer in isUpToDateConsumers) {
      consumer.isUpToDate(project, isUpToDate)
    }
  }
}
