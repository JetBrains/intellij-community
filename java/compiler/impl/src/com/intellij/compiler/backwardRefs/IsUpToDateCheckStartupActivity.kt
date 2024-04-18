// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler.backwardRefs

import com.intellij.compiler.impl.CompileDriver
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.compiler.CompilerManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

/**
 * @see IsUpToDateCheckConsumer
 */
internal class IsUpToDateCheckStartupActivity : ProjectActivity {
  init {
    if (ApplicationManager.getApplication().isUnitTestMode) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override suspend fun execute(project: Project) {
    val logger = thisLogger()

    val isUpToDateConsumers = blockingContext {
      IsUpToDateCheckConsumer.EP_NAME.extensionList.filter { it.isApplicable(project) }
    }
    if (isUpToDateConsumers.isEmpty()) {
      logger.info("suitable consumer is not found")
      return
    }
    // Triggering project save activity to ensure that we don't violate the contract of JPS execution (.idea folder has to be available)
    project.save()

    coroutineContext.ensureActive()
    logger.info("activity started")
    val isUpToDate = nonBlockingIsUpToDate(project)

    logger.info("isUpToDate = $isUpToDate")
    for (consumer in isUpToDateConsumers) {
      consumer.isUpToDate(project, isUpToDate)
      coroutineContext.ensureActive()
    }
  }

  @Suppress("DuplicatedCode")
  private suspend fun nonBlockingIsUpToDate(project: Project): Boolean {
    return coroutineToIndicator {
      val manager = CompilerManager.getInstance(project)
      val scope = manager.createProjectCompileScope(project)
      CompileDriver.setCompilationStartedAutomatically(scope)
      manager.isUpToDate(scope, ProgressManager.getInstance().progressIndicator)
    }
  }

}
