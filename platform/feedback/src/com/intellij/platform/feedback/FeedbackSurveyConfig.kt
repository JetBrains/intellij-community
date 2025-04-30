// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.feedback.impl.bundle.CommonFeedbackBundle
import com.intellij.platform.feedback.impl.checkIsFeedbackCollectionDeadlineNotPast
import com.intellij.platform.feedback.impl.checkIsIdeEAPIfRequired
import com.intellij.platform.feedback.impl.notification.RequestFeedbackNotification
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import kotlinx.datetime.LocalDate
import org.jetbrains.annotations.Nls

/**
 * Represents the base config for feedback survey.
 *
 * @see com.intellij.platform.feedback.InIdeFeedbackSurveyConfig
 * @see com.intellij.platform.feedback.ExternalFeedbackSurveyConfig
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
   * Whether the survey is allowed to be shown indefinite times. Must only be used by product and not plugins.
   */
  val isIndefinite: Boolean
    get() = false

  /**
   * Checks whether the IDE is suitable for the feedback survey.
   *
   * Usually needed when you want to show a survey only to users of a particular IDE.
   */
  fun checkIdeIsSuitable(): Boolean

  /**
   * Checks whether the extra conditions for showing the survey notification are satisfied.
   *
   * Usually needed when you want to show a survey only to users who have already used some feature.
   * Or when you want to show a survey only to users of a particular version of the IDE.
   */
  fun checkExtraConditionSatisfied(project: Project): Boolean

  /**
   * Checks whether the extra conditions for showing the survey by an explicit user action are satisfied.
   *
   * The same as [checkExtraConditionSatisfied] but for the case when the user intentionally submits
   * feedback through an explicit feature-specific action and not through a regular survey notification.
   *
   * Normally it imposes less restrictions, as, for example, [checkExtraConditionSatisfied] might check whether
   * the user has been using a specific feature long enough before showing a survey notification,
   * but when the user explicitly wants to provide feedback already, such conditions are usually irrelevant.
   */
  fun checkExtraConditionSatisfiedForExplicitUserAction(project: Project): Boolean = true

  /**
   * Returns a notification encouraging the user to leave feedback, which will be shown to the user.
   */
  fun createNotification(project: Project, forTest: Boolean): RequestFeedbackNotification

  /**
   * Performs additional custom state updates after a notification showed.
   */
  fun updateStateAfterNotificationShowed(project: Project)

  /**
   * Returns the label for the response action that will be shown in the notification.
   */
  @Nls
  fun getRespondNotificationActionLabel(): String {
    return CommonFeedbackBundle.message("notification.request.feedback.action.respond.text")
  }

  /**
   * Returns the label for the `Don't show again` action that will be shown in the notification.
   */
  @Nls
  fun getCancelNotificationActionLabel(): String {
    return CommonFeedbackBundle.message("notification.request.feedback.action.dont.show.text")
  }

  /**
   * Returns the action that will be invoked if the user invokes the action to show no more survey notifications.
   *
   * Usually needed to show the user another notification of where they can leave feedback later.
   */
  fun getCancelNotificationAction(project: Project): () -> Unit {
    return {}
  }

}

/**
 * Checks whether it's possible for the user to explicitly submit feedback.
 *
 * Intended to be used by feature-specific feedback actions,
 * where this function is used for [com.intellij.openapi.actionSystem.AnAction.update]
 * and [com.intellij.platform.feedback.InIdeFeedbackSurveyConfig.showFeedbackDialog]
 * is used for [com.intellij.openapi.actionSystem.AnAction.actionPerformed].
 */
@RequiresBackgroundThread
fun FeedbackSurveyConfig.isSuitableToShowByExplicitUserAction(project: Project): Boolean {
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
    checkIsFeedbackCollectionDeadlineNotPast() &&
    checkIsIdeEAPIfRequired()
  }
  return commonConditionsForAllSurveys && checkExtraConditionSatisfiedForExplicitUserAction(project)
}
