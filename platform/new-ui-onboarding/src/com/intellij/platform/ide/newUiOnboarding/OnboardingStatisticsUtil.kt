// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.newUiOnboarding

import com.intellij.internal.statistic.eventLog.events.EventFields

internal object OnboardingStatisticsUtil {
  val stepIdField = EventFields.StringValidatedByCustomRule<NewUiOnboardingStepIdRule>("step_id")
  val durationField = EventFields.DurationMs
  val lastStepDurationField = EventFields.Long("last_step_duration_ms")
  val stopReasonField = EventFields.Enum<OnboardingStopReason>("reason")

  fun getDuration(startMillis: Long): Long = System.currentTimeMillis() - startMillis

  enum class OnboardingStopReason {
    SKIP_ALL, ESCAPE_PRESSED, PROJECT_CLOSED
  }
}