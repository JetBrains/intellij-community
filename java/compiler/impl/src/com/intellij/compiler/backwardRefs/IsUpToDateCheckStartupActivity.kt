// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler.backwardRefs

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.compiler.CompilerManager
import com.intellij.openapi.compiler.JavaCompilerBundle
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.progress.withBackgroundProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectPostStartupActivity
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

/**
 * @see IsUpToDateCheckConsumer
 */
internal class IsUpToDateCheckStartupActivity : ProjectPostStartupActivity {
  init {
    if (ApplicationManager.getApplication().isUnitTestMode) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override suspend fun execute(project: Project) {
    val logger = thisLogger()

    val isUpToDateConsumers = IsUpToDateCheckConsumer.EP_NAME.extensionList.filter { it.isApplicable(project) }
    if (isUpToDateConsumers.isEmpty()) {
      logger.info("suitable consumer is not found")
      return
    }
    else {
      logger.info("activity started")
    }

    coroutineContext.ensureActive()

    val isUpToDate = withBackgroundProgressIndicator(project, JavaCompilerBundle.message("refresh.compiler.ref.index")) {
      nonBlockingIsUpToDate(project)
    }

    logger.info("isUpToDate = $isUpToDate")
    for (consumer in isUpToDateConsumers) {
      consumer.isUpToDate(project, isUpToDate)
      coroutineContext.ensureActive()
    }
  }

  @Suppress("DuplicatedCode")
  suspend fun nonBlockingIsUpToDate(project: Project): Boolean {
    return coroutineToIndicator {
      val manager = CompilerManager.getInstance(project)
      manager.isUpToDate(manager.createProjectCompileScope(project), ProgressManager.getInstance().progressIndicator)
    }
  }

}
