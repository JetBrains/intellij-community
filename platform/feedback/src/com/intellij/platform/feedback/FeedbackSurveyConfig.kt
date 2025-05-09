// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback

import com.intellij.openapi.project.Project
import com.intellij.platform.feedback.impl.bundle.CommonFeedbackBundle
import com.intellij.platform.feedback.impl.notification.RequestFeedbackNotification
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
