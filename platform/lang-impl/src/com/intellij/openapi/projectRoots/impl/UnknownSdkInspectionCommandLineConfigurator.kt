// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl

import com.intellij.ide.CommandLineInspectionProjectConfigurator
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.withPushPop
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectBundle
import com.intellij.openapi.util.registry.Registry
import org.jetbrains.annotations.NotNull

class UnknownSdkInspectionCommandLineConfigurator : CommandLineInspectionProjectConfigurator {
  companion object {
    private val LOG: Logger = logger<UnknownSdkInspectionCommandLineConfigurator>()

    fun configureUnknownSdks(project: Project, indicator: ProgressIndicator) {
      require(!ApplicationManager.getApplication().isWriteThread) {
        "The code below uses the same GUI thread to complete operations. Running from EDT would deadlock"
      }
      ApplicationManager.getApplication().assertIsNonDispatchThread()


      indicator.withPushPop {
        indicator.text = ProjectBundle.message("config.unknown.progress.scanning")

        val problems = UnknownSdkTracker
          .getInstance(project)
          .collectUnknownSdks(UnknownSdkCollector(project), indicator)

        if (problems.isEmpty()) return@withPushPop
        indicator.isIndeterminate = false

        for ((i, problem) in problems.withIndex()) {
          val fix = problem.suggestedFixAction

          if (fix == null) {
            LOG.warn("Failed to resolve ${problem.sdkTypeAndNameText}: ${problem.notificationText}")
            continue
          }

          LOG.info("Resolving ${problem.sdkTypeAndNameText}")
          indicator.withPushPop {
            indicator.fraction = i.toDouble() / problems.size
            indicator.text = ProjectBundle.message("config.unknown.progress.configuring", problem.sdkTypeAndNameText)
            fix.applySuggestionBlocking(indicator)
          }
        }
      }
    }
  }

  override fun getName() = "sdk"

  override fun getDescription(): String = ProjectBundle.message("config.unknown.sdk.commandline.configure")

  override fun isApplicable(context: CommandLineInspectionProjectConfigurator.ConfiguratorContext): Boolean =
    !ApplicationManager.getApplication().isUnitTestMode

  override fun configureEnvironment(context: CommandLineInspectionProjectConfigurator.ConfiguratorContext) {
    Registry.get("unknown.sdk.auto").setValue(false) // forbid UnknownSdkTracker post startup activity as we run it here
  }

  override fun configureProject(project: Project, context: CommandLineInspectionProjectConfigurator.ConfiguratorContext) {
    configureUnknownSdks(project, context.progressIndicator)
  }
}
