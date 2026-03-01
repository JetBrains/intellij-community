// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.newUiOnboarding.newUi

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.ide.newUiOnboarding.NewUiOnboardingStep
import com.intellij.platform.ide.newUiOnboarding.newUi.NewUiOnboardingStatistics.OnboardingStartingPlace
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
    return stepIds.mapNotNull { id ->
      val step = NewUiOnboardingStep.getIfAvailable(id)
      if (step != null) id to step else null
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
    return listOf("toolWindowsNames",
                  "moreToolWindows",
                  "settingsAndCustomization",
                  "mainMenu",
                  "projectWidget",
                  "gitWidget",
                  "runWidget",
                  "navigationBar")
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): NewUiOnboardingService = project.service()
  }
}