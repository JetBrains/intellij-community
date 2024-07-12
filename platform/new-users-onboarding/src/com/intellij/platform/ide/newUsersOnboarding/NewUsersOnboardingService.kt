// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.newUsersOnboarding

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.ide.newUiOnboarding.NewUiOnboardingStep
import com.intellij.platform.ide.newUsersOnboarding.NewUsersOnboardingStatistics.OnboardingStartingPlace
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope

@Service(Service.Level.PROJECT)
internal class NewUsersOnboardingService(private val project: Project, private val coroutineScope: CoroutineScope) {
  // Should be accessed only in EDT
  private var currentExecutor: NewUsersOnboardingExecutor? = null

  fun showOnboardingDialog() {
    val dialog = NewUsersOnboardingDialog(project)
    NewUsersOnboardingStatistics.logDialogShown(project)
    val startTour = dialog.showAndGet()
    if (startTour) {
      startOnboarding()
      NewUsersOnboardingStatistics.logOnboardingStarted(project, OnboardingStartingPlace.DIALOG)
    }
    else {
      // It triggers on the notification text, while it is expected to have capitalized words because it is the terms.
      @Suppress("DialogTitleCapitalization")
      Notification(
        "newUsersOnboarding",
        NewUsersOnboardingBundle.message("notification.text"),
        NotificationType.INFORMATION
      ).notify(project)

      NewUsersOnboardingStatistics.logDialogSkipPressed(project)
    }
  }

  fun startOnboarding() {
    // Interrupt the running tour if any
    currentExecutor?.finishOnboarding(NewUsersOnboardingStatistics.OnboardingStopReason.INTERRUPTED)

    val steps = getSteps()
    val childScope = coroutineScope.childScope("onboarding executor")
    val executor = NewUsersOnboardingExecutor(project, steps, childScope, project) {
      currentExecutor = null
    }
    currentExecutor = executor
    executor.start()
  }

  private fun getSteps(): List<Pair<String, NewUiOnboardingStep>> {
    val stepIds = getStepsOrder()
    return stepIds.mapNotNull { id ->
      val step = NewUiOnboardingStep.getIfAvailable(id)
      if (step != null) id to step else null
    }
  }

  private fun getStepsOrder(): List<String> {
    return listOf(
      "mainMenu",
      "projectWidget",
      "gitWidget",
      "runWidget",
      "searchEverywhere",
      "toolWindowLayouts",
      "moreToolWindows",
      "learnToolWindow"
    )
  }

  companion object {
    fun getInstance(project: Project): NewUsersOnboardingService = project.service()
  }
}