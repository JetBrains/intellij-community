// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler.backwardRefs

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.compiler.CompilerManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity

/**
 * Marked CRI as "up to date" if the project is already compiled.
 *
 * @see IsUpToDateCheckListener
 */
class CompilerReferenceIndexIsUpToDateStartupActivity : StartupActivity.Background {
  override fun runActivity(project: Project) {
    if (!CompilerReferenceServiceBase.isEnabled() || ApplicationManager.getApplication().isUnitTestMode) return

    val logger = thisLogger()
    logger.info("CRI activity started")

    val compilerManager = CompilerManager.getInstance(project)
    val projectCompileScope = compilerManager.createProjectCompileScope(project)
    val isUpToDate = compilerManager.isUpToDate(projectCompileScope)

    logger.info("CRI activity result: isUpToDate = $isUpToDate")
    project.messageBus.syncPublisher(IsUpToDateCheckListener.TOPIC).isUpToDateCheckFinished(isUpToDate)
  }
}
