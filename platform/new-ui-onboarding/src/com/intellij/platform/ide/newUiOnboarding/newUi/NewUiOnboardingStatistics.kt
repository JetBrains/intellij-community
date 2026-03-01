// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.newUiOnboarding.newUi

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.project.Project
import com.intellij.platform.ide.newUiOnboarding.OnboardingStatisticsUtil.durationField
import com.intellij.platform.ide.newUiOnboarding.OnboardingStatisticsUtil.getDuration
import com.intellij.platform.ide.newUiOnboarding.OnboardingStatisticsUtil.lastStepDurationField
import com.intellij.platform.ide.newUiOnboarding.OnboardingStatisticsUtil.stepIdField

internal object NewUiOnboardingStatistics : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup = GROUP

  private val GROUP: EventLogGroup = EventLogGroup("new.ui.onboarding", 4)

  enum class OnboardingStartingPlace {
    WELCOME_DIALOG
  }

  enum class OnboardingStopReason {
    SKIP_ALL, ESCAPE_PRESSED, PROJECT_CLOSED
  }

  private val startingPlaceField = EventFields.Enum<OnboardingStartingPlace>("starting_place")
  private val stopReasonField = EventFields.Enum<OnboardingStopReason>("reason")

  private val welcomeDialogShownEvent = GROUP.registerEvent("welcome.dialog.shown")
  private val welcomeDialogSkipEvent = GROUP.registerEvent("welcome.dialog.skip.clicked")
  private val onboardingStartedEvent = GROUP.registerEvent("started", startingPlaceField)
  private val onboardingStoppedEvent = GROUP.registerVarargEvent("stopped", stepIdField, stopReasonField,
                                                                 durationField, lastStepDurationField)
  private val onboardingFinishedEvent = GROUP.registerEvent("finished", durationField)
  private val stepStartedEvent = GROUP.registerEvent("step.started", stepIdField)
  private val stepFinishedEvent = GROUP.registerEvent("step.finished", stepIdField, durationField)
  private val linkClickedEvent = GROUP.registerEvent("link.clicked", stepIdField)

  fun logWelcomeDialogShown(project: Project) {
    welcomeDialogShownEvent.log(project)
  }

  fun logWelcomeDialogSkipPressed(project: Project) {
    welcomeDialogSkipEvent.log(project)
  }

  fun logOnboardingStarted(project: Project, place: OnboardingStartingPlace) {
    onboardingStartedEvent.log(project, place)
  }

  fun logOnboardingStopped(project: Project, stepId: String, reason: OnboardingStopReason, startMillis: Long, lastStepStartMillis: Long) {
    onboardingStoppedEvent.log(project,
                               stepIdField with stepId,
                               stopReasonField with reason,
                               durationField with getDuration(startMillis),
                               lastStepDurationField with getDuration(lastStepStartMillis))
  }

  fun logOnboardingFinished(project: Project, startMillis: Long) {
    onboardingFinishedEvent.log(project, getDuration(startMillis))
  }

  fun logStepStarted(project: Project, stepId: String) {
    stepStartedEvent.log(project, stepId)
  }

  fun logStepFinished(project: Project, stepId: String, startMillis: Long) {
    stepFinishedEvent.log(project, stepId, getDuration(startMillis))
  }

  fun logLinkClicked(project: Project, stepId: String) {
    linkClickedEvent.log(project, stepId)
  }
}