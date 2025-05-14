// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback

import com.intellij.openapi.project.Project
import kotlinx.datetime.LocalDate

/**
 * Represents the base config for feedback surveys.
 */
interface FeedbackSurveyConfig {
  /**
   * Unique identifier reflecting the survey.
   *
   * Used for automatic collection of FUS statistics.
   */
  val surveyId: String

  /**
   * Date of the last day of feedback collection.
   *
   * Used to not keep collecting user feedback when it's no longer needed.
   */
  val lastDayOfFeedbackCollection: LocalDate

  /**
   * Whether the IDE must be of EAP version.
   */
  val requireIdeEAP: Boolean

  /**
   * Checks whether the IDE is suitable for the feedback survey.
   *
   * Usually needed when you want to show a survey only to users of a particular IDE.
   */
  fun checkIdeIsSuitable(): Boolean

  /**
   * Checks whether the extra conditions for showing the survey are satisfied.
   *
   * Usually needed when you want to show a survey only to users who have already used some feature.
   * Or when you want to show a survey only to users of a particular version of the IDE.
   */
  fun checkExtraConditionSatisfied(project: Project): Boolean
}