// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.newUsersOnboarding

import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ConfigImportHelper
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.platform.ide.newUiOnboarding.NewUiOnboardingStep
import com.intellij.platform.ide.newUsersOnboarding.NewUsersOnboardingStatistics.OnboardingStartingPlace
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class NewUsersOnboardingService(private val project: Project, private val coroutineScope: CoroutineScope) {
  // Should be accessed only in EDT
  private var currentExecutor: NewUsersOnboardingExecutor? = null
  private var currentDialog: DialogWrapper? = null

  var wasDialogShownDuringIdeSession: Boolean = false
    private set

  fun showOnboardingDialog() {
    // Close with the other exit code to not trigger showing of notification
    currentDialog?.close(NewUsersOnboardingDialog.CLOSE_EXTERNALLY)

    val dialog = NewUsersOnboardingDialog(project, this::onDialogClosed)
    currentDialog = dialog
    dialog.show()

    wasDialogShownDuringIdeSession = true
    PropertiesComponent.getInstance().setValue(NEW_USERS_ONBOARDING_DIALOG_SHOWN_PROPERTY, true)
    NewUsersOnboardingStatistics.logDialogShown(project)
  }

  private fun onDialogClosed(exitCode: Int) {
    currentDialog = null

    when (exitCode) {
      DialogWrapper.OK_EXIT_CODE -> {
        startOnboarding()
        NewUsersOnboardingStatistics.logOnboardingStarted(project, OnboardingStartingPlace.DIALOG)
      }
      DialogWrapper.CLOSE_EXIT_CODE -> {
        // It triggers on the notification text, while it is expected to have capitalized words because it is the terms.
        @Suppress("DialogTitleCapitalization")
        Notification(
          "newUsersOnboarding",
          NewUsersOnboardingBundle.message("notification.text"),
          NotificationType.INFORMATION
        ).notify(project)

        NewUsersOnboardingStatistics.logDialogSkipPressed(project)
      }
      else -> {
        // do nothing
      }
    }
  }

  fun shouldShowOnboardingDialog(): Boolean {
    return service<NewUsersOnboardingExperiment>().isEnabled() &&
           !PropertiesComponent.getInstance().getBoolean(NEW_USERS_ONBOARDING_DIALOG_SHOWN_PROPERTY) &&
           ConfigImportHelper.isNewUser()
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
      "settingsPopup",
      "toolWindowLayouts",
      "moreToolWindows",
      "learnToolWindow"
    )
  }

  companion object {
    fun getInstance(project: Project): NewUsersOnboardingService = project.service()

    private const val NEW_USERS_ONBOARDING_DIALOG_SHOWN_PROPERTY: String = "NEW_USERS_ONBOARDING_DIALOG_SHOWN"
  }
}