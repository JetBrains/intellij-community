// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.newUiOnboarding

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.validator.ValidationResultType
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.internal.statistic.utils.getPluginInfo

internal class NewUiOnboardingStatistics : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup = GROUP

  enum class OnboardingStartingPlace {
    WELCOME_DIALOG, CONFIGURE_NEW_UI_TOOLWINDOW
  }

  enum class OnboardingStopReason {
    SKIP_ALL, ESCAPE_PRESSED, PROJECT_CLOSED
  }

  companion object {
    private val GROUP: EventLogGroup = EventLogGroup("newUiOnboarding", 1)

    private val stepIdField = EventFields.StringValidatedByCustomRule<NewUiOnboardingStepIdRule>("stepId")
    private val durationField = EventFields.Long("duration")
    private val lastStepDurationField = EventFields.Long("lastStepDuration")
    private val stopReasonField = EventFields.Enum<OnboardingStopReason>("reason")
    private val startingPlaceField = EventFields.Enum<OnboardingStartingPlace>("starting_place")

    private val welcomeDialogShownEvent = GROUP.registerEvent("welcomeDialog.shown")
    private val welcomeDialogSkipEvent = GROUP.registerEvent("welcomeDialog.skip")
    private val onboardingStartedEvent = GROUP.registerEvent("started", startingPlaceField)
    private val onboardingStoppedEvent = GROUP.registerVarargEvent("stopped", stepIdField, stopReasonField,
                                                                   durationField, lastStepDurationField)
    private val onboardingFinishedEvent = GROUP.registerEvent("finished", durationField)
    private val stepStartedEvent = GROUP.registerEvent("step.started", stepIdField)
    private val stepFinishedEvent = GROUP.registerEvent("step.finished", stepIdField, durationField)

    fun logWelcomeDialogShown() {
      welcomeDialogShownEvent.log()
    }

    fun logWelcomeDialogSkipPressed() {
      welcomeDialogSkipEvent.log()
    }

    fun logOnboardingStarted(place: OnboardingStartingPlace) {
      onboardingStartedEvent.log(place)
    }

    fun logOnboardingStopped(stepId: String, reason: OnboardingStopReason, startMillis: Long, lastStepStartMillis: Long) {
      onboardingStoppedEvent.log(stepIdField with stepId,
                                 stopReasonField with reason,
                                 durationField with getDuration(startMillis),
                                 lastStepDurationField with getDuration(lastStepStartMillis))
    }

    fun logOnboardingFinished(startMillis: Long) {
      onboardingFinishedEvent.log(getDuration(startMillis))
    }

    fun logStepStarted(stepId: String) {
      stepStartedEvent.log(stepId)
    }

    fun logStepFinished(stepId: String, startMillis: Long) {
      stepFinishedEvent.log(stepId, getDuration(startMillis))
    }

    private fun getDuration(startMillis: Long): Long = System.currentTimeMillis() - startMillis
  }
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