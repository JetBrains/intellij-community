// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback

import com.intellij.openapi.project.Project
import com.intellij.platform.feedback.impl.bundle.CommonFeedbackBundle
import com.intellij.platform.feedback.impl.notification.RequestFeedbackNotification
import org.jetbrains.annotations.Nls

/**
 * Represents the base config for showing feedback survey notifications.
 *
 * @see com.intellij.platform.feedback.InIdeFeedbackSurveyConfig
 * @see com.intellij.platform.feedback.ExternalFeedbackSurveyConfig
 */
interface NotificationBasedFeedbackSurveyConfig : FeedbackSurveyConfig {

  /**
   * Checks whether the extra conditions for showing the survey notification are satisfied.
   *
   * For a notification to be shown, both this and [checkExtraConditionSatisfied] must return `true`.
   * This function, along with [ActionBasedFeedbackConfig.checkExtraConditionSatisfiedForAction]
   * is intended to be used in cases when the conditions for notification-based and action-based
   * surveys are different.
   */
  fun checkExtraConditionSatisfiedForNotification(project: Project): Boolean = true

  /**
   * Whether the survey is allowed to be shown indefinite times. Must only be used by product and not plugins.
   */
  val isIndefinite: Boolean
    get() = false

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
