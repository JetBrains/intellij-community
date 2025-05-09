// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.feedback.dialog.BlockBasedFeedbackDialog
import com.intellij.platform.feedback.dialog.SystemDataJsonSerializable
import com.intellij.platform.feedback.impl.checkIsFeedbackCollectionDeadlineNotPast
import com.intellij.platform.feedback.impl.checkIsIdeEAPIfRequired
import com.intellij.platform.feedback.impl.state.CommonFeedbackSurveyService
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.datetime.LocalDate

/**
 * Represents the config of a feedback action that can be explicitly invoked by the user.
 *
 * Similar to [InIdeFeedbackSurveyConfig], but instead of asking the user to submit feedback,
 * this config is intended to be used in cases when feedback is submitted by an explicit user
 * action (e.g., an action button, an action link, etc.).
 */
interface ExplicitUserFeedbackConfig {

  /**
   * Unique identifier reflecting the survey.
   *
   * If the same feedback can be provided both by an in-IDE notification using [InIdeFeedbackSurveyConfig]
   * and by an explicit action, then this survey ID must be equal to that of the in-IDE survey config
   * to prevent showing survey notifications if the user has already sent feedback explicitly.
   */
  val surveyId: String

  /**
   * Date of the last day of feedback collection.
   *
   * Used to not letting the user submit feedback when it's no longer relevant.
   *
   * Often set to something like the planned date of the next release.
   */
  val lastDayOfFeedbackCollection: LocalDate

  /**
   * Whether the IDE must be of a EAP version.
   */
  val requireIdeEAP: Boolean

  /**
   * Checks whether the IDE is suitable for the feedback survey.
   *
   * Usually needed when you want to show a survey only to users of a particular IDE.
   */
  fun checkIdeIsSuitable(): Boolean

  /**
   * Checks whether the extra conditions for submitting feedback are satisfied.
   *
   * Normally it imposes fewer restrictions than [InIdeFeedbackSurveyConfig.checkExtraConditionSatisfied],
   * as, for example, there may be a check whether the user has been using a specific feature long enough
   * before showing a survey notification.
   * But when the user explicitly invoked an action to submit feedback, such checks are not needed.
   *
   * This check might include, for example, a check that the feature to submit feedback about is currently enabled.
   */
  fun checkExtraConditionSatisfied(project: Project): Boolean = true

  /**
   * Returns a dialog with the feedback survey.
   *
   * @see BlockBasedFeedbackDialog
   */
  fun createFeedbackDialog(project: Project, forTest: Boolean): BlockBasedFeedbackDialog<out SystemDataJsonSerializable>

  /**
   * Performs additional custom state updates after the feedback dialog is closed by the ok button.
   */
  fun updateStateAfterDialogClosedOk(project: Project)
}

/**
 * Checks whether it's possible for the user to submit feedback now.
 *
 * Intended to be used in [com.intellij.openapi.actionSystem.AnAction.update].
 *
 * @see showFeedbackDialog
 */
@RequiresBackgroundThread
fun ExplicitUserFeedbackConfig.isSuitableToShow(project: Project): Boolean {
  val commonConditionsForAllSurveys = if (Registry.`is`("platform.feedback.ignore.common.conditions.for.all.surveys", false)) {
    true
  }
  else {
    // Only a subset is checked as compared to com.intellij.platform.feedback.impl.FeedbackSurveyUtilsKt.isSuitableToShow.
    // This is because if the user wants to explicitly submit feedback, we shouldn't prevent it just because the feedback is already sent,
    // or because a feedback notification was shown several times.
    // But we still want feedback submission to be available only for the right IDE, right EAP/release mode
    // and don't want to allow outdated feedback.
    checkIdeIsSuitable() &&
    checkIsFeedbackCollectionDeadlineNotPast(lastDayOfFeedbackCollection) &&
    checkIsIdeEAPIfRequired(requireIdeEAP)
  }
  return commonConditionsForAllSurveys && checkExtraConditionSatisfied(project)
}

/**
 * Shows the feedback dialog for the user to submit feedback.
 *
 * Intended to be used in [com.intellij.openapi.actionSystem.AnAction.actionPerformed].
 *
 * @see isSuitableToShow
 */
@RequiresEdt
fun ExplicitUserFeedbackConfig.showFeedbackDialog(project: Project, forTest: Boolean) {
  val dialog = createFeedbackDialog(project, forTest)
  val isOk = dialog.showAndGet()
  if (isOk && !forTest) {
    updateStateAfterDialogClosedOk(project)
    CommonFeedbackSurveyService.feedbackSurveyAnswerSent(surveyId)
  }
}
