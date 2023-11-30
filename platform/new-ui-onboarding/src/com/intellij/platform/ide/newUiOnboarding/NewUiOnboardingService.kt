// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.newUiOnboarding

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.platform.ide.newUiOnboarding.NewUiOnboardingStatistics.OnboardingStartingPlace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Service(Service.Level.PROJECT)
internal class NewUiOnboardingService(private val project: Project, private val cs: CoroutineScope) {
  fun showOnboardingDialog() {
    val dialog = NewUiOnboardingDialog(project)
    NewUiOnboardingStatistics.logWelcomeDialogShown(project)
    val startTour = dialog.showAndGet()
    if (startTour) {
      startOnboarding()
      NewUiOnboardingStatistics.logOnboardingStarted(project, OnboardingStartingPlace.WELCOME_DIALOG)
    }
    else {
      val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.MEET_NEW_UI)
      toolWindow?.activate(null)
      NewUiOnboardingStatistics.logWelcomeDialogSkipPressed(project)
    }
  }

  fun startOnboarding() {
    val steps = getSteps()
    val executor = NewUiOnboardingExecutor(project, steps, cs, project)
    cs.launch(Dispatchers.EDT) { executor.start() }
  }

  private fun getSteps(): List<Pair<String, NewUiOnboardingStep>> {
    val stepIds = getStepOrder()
    val stepExtensions = NewUiOnboardingStep.EP_NAME.extensionList
    return stepIds.mapNotNull { id ->
      val step = stepExtensions.find { it.key == id }?.instance
      if (step?.isAvailable() == true) {
        id to step
      }
      else null
    }
  }

  private fun getStepOrder(): List<String> {
    val defaultOrder = getDefaultStepsOrder()
    val customizations = NewUiOnboardingBean.getInstance().customizations
    if (customizations.isEmpty()) {
      return defaultOrder
    }
    val mutableSteps = defaultOrder.toMutableList()
    for (customization in customizations) {
      customization.customize(mutableSteps)
    }
    return mutableSteps
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