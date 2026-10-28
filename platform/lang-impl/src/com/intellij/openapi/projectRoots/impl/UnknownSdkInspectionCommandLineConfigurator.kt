// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots.impl

import com.intellij.ide.CommandLineInspectionProjectAsyncConfigurator
import com.intellij.ide.CommandLineInspectionProjectConfigurator
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectBundle
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.impl.WorkspaceModelInternal
import com.intellij.platform.util.progress.reportSequentialProgress
import com.intellij.platform.util.progress.withProgressText

private val LOG: Logger = logger<UnknownSdkInspectionCommandLineConfigurator>()

internal class UnknownSdkInspectionCommandLineConfigurator : CommandLineInspectionProjectAsyncConfigurator {

  override fun getName(): String = "sdk"

  override fun getDescription(): String = ProjectBundle.message("config.unknown.sdk.commandline.configure")

  override fun shouldBeInvokedAlongsideActivityTracking(): Boolean {
    return true
  }

  override fun isApplicable(context: CommandLineInspectionProjectConfigurator.ConfiguratorContext): Boolean =
    !ApplicationManager.getApplication().isUnitTestMode

  override fun configureEnvironment(context: CommandLineInspectionProjectConfigurator.ConfiguratorContext) {
    System.setProperty("unknown.sdk.auto", false.toString())
  }

  override suspend fun configureProjectAsync(project: Project, context: CommandLineInspectionProjectConfigurator.ConfiguratorContext) {
    configureUnknownSdks(project)
  }
}

internal suspend fun configureUnknownSdks(project: Project) {
  require(!ApplicationManager.getApplication().isWriteIntentLockAcquired) {
    "The code below uses the same GUI thread to complete operations. Running from EDT would deadlock"
  }
  ApplicationManager.getApplication().assertIsNonDispatchThread()

  UnknownSdkActivityFinishedService.getInstance(project).await()
}

private fun fixUnknownSdks(project: Project, indicator: ProgressIndicator) {
  indicator.withPushPop {
    indicator.text = ProjectBundle.message("config.unknown.progress.scanning")
internal suspend fun runUnknownSdkFix(project: Project) {
  fixUnknownSdks(project)
}

private suspend fun fixUnknownSdks(project: Project) {
  val problems = withProgressText(ProjectBundle.message("config.unknown.progress.scanning")) {
    coroutineToIndicator { indicator ->
      UnknownSdkTracker.getInstance(project).collectUnknownSdks(UnknownSdkCollector(project), indicator)
    }
  }

  if (problems.isEmpty()) return

  reportSequentialProgress(problems.size) { reporter ->
    for (problem in problems) {
      val fix = problem.suggestedFixAction
      val progressText = ProjectBundle.message("config.unknown.progress.configuring", problem.sdkTypeAndNameText)

      if (fix == null) {
        LOG.warn("Failed to resolve ${problem.sdkTypeAndNameText}: ${problem.notificationText}")
        reporter.itemStep(progressText)
        continue
      }

      LOG.info("Resolving ${problem.sdkTypeAndNameText}")
      reporter.itemStep(progressText) {
        coroutineToIndicator { indicator -> fix.applySuggestionBlocking(indicator) }
      }
    }
  }
}
