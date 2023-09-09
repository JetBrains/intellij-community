// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.newUiOnboarding

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.validator.ValidationResultType
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.internal.statistic.utils.getPluginInfo
import com.intellij.openapi.project.Project

internal object NewUiOnboardingStatistics : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup = GROUP

  enum class OnboardingStartingPlace {
    WELCOME_DIALOG, CONFIGURE_NEW_UI_TOOLWINDOW
  }

  enum class OnboardingStopReason {
    SKIP_ALL, ESCAPE_PRESSED, PROJECT_CLOSED
  }

  private val GROUP: EventLogGroup = EventLogGroup("new.ui.onboarding", 3)

  private val stepIdField = EventFields.StringValidatedByCustomRule<NewUiOnboardingStepIdRule>("step_id")
  private val durationField = EventFields.DurationMs
  private val lastStepDurationField = EventFields.Long("last_step_duration_ms")
  private val stopReasonField = EventFields.Enum<OnboardingStopReason>("reason")
  private val startingPlaceField = EventFields.Enum<OnboardingStartingPlace>("starting_place")

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

  private fun getDuration(startMillis: Long): Long = System.currentTimeMillis() - startMillis
}

internal class NewUiOnboardingStepIdRule : CustomValidationRule() {
  override fun getRuleId(): String = "newUiOnboardingStepId"

  override fun doValidate(data: String, context: EventContext): ValidationResultType {
    val step = NewUiOnboardingStep.EP_NAME.findFirstSafe { it.key == data }
    if (step == null) {
      return ValidationResultType.REJECTED
    }
    val isDevelopedByJB = getPluginInfo(step.implementationClass).isDevelopedByJetBrains()
    return if (isDevelopedByJB) ValidationResultType.ACCEPTED else ValidationResultType.THIRD_PARTY
  }
}