// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.newUiOnboarding

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Service(Service.Level.PROJECT)
internal class NewUiOnboardingService(private val project: Project, private val cs: CoroutineScope) {
  fun showOnboardingDialog() {
    val dialog = NewUiOnboardingDialog(project)
    val startTour = dialog.showAndGet()
    if (startTour) {
      startOnboarding()
    }
    else {
      val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.MEET_NEW_UI)
      toolWindow?.activate(null)
    }
  }

  fun startOnboarding() {
    val steps = getSteps()
    val executor = NewUiOnboardingExecutor(project, steps, cs, project)
    cs.launch(Dispatchers.EDT) { executor.start() }
  }

  private fun getSteps(): List<NewUiOnboardingStep> {
    // todo: add an ability to provide custom steps order (for other IDEs that want to customize the onboarding)
    val stepIds = getDefaultStepsOrder()
    val stepExtensions = NewUiOnboardingStep.EP_NAME.extensions
    return stepIds.mapNotNull { id ->
      val step = stepExtensions.find { it.key == id }?.instance
      step?.takeIf { it.isAvailable() }
    }
  }

  private fun getDefaultStepsOrder(): List<String> {
    return listOf("mainMenu",
                  "projectWidget",
                  "gitWidget",
                  "runWidget",
                  "codeWithMe",
                  "toolWindowLayouts",
                  "moreToolWindows",
                  "navigationBar")
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): NewUiOnboardingService = project.service()
  }
}