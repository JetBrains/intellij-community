// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback

import com.intellij.feedback.common.state.CommonFeedbackSurveyService
import com.intellij.feedback.common.state.DontShowAgainFeedbackService
import com.intellij.notification.NotificationAction
import com.intellij.openapi.project.Project

sealed class FeedbackSurveyType<T : FeedbackSurveyConfig> {

  companion object {
    private const val MAX_FEEDBACK_SURVEY_NUMBER_SHOWS: Int = 2
  }

  protected abstract val feedbackSurveyConfig: T

  abstract fun getRespondNotificationAction(project: Project, forTest: Boolean): () -> Unit

  fun updateCommonFeedbackSurveysStateAfterSent() {
    CommonFeedbackSurveyService.feedbackSurveyAnswerSent(feedbackSurveyConfig.surveyId)
  }

  fun isSuitableToShow(project: Project): Boolean {
    return !CommonFeedbackSurveyService.checkIsFeedbackSurveyAnswerSent(feedbackSurveyConfig.surveyId) &&
           feedbackSurveyConfig.checkIdeIsSuitable() &&
           feedbackSurveyConfig.checkIsFeedbackCollectionDeadlineNotPast() &&
           feedbackSurveyConfig.checkIsIdeEAPIfRequired() &&
           checkNumberShowsNotExceeded() &&
           feedbackSurveyConfig.checkExtraConditionSatisfied(project)
  }

  fun showNotification(project: Project, forTest: Boolean) {
    val notification = feedbackSurveyConfig.createNotification(project, forTest)
    notification.addAction(
      NotificationAction.createSimpleExpiring(feedbackSurveyConfig.getRespondNotificationActionLabel()) {
        if (!forTest) {
          CommonFeedbackSurveyService.feedbackSurveyRespondActionInvoked(feedbackSurveyConfig.surveyId)
        }
        getRespondNotificationAction(project, forTest)()
      }
    )
    notification.addAction(
      NotificationAction.createSimpleExpiring(feedbackSurveyConfig.getCancelNotificationActionLabel()) {
        if (!forTest) {
          DontShowAgainFeedbackService.dontShowFeedbackInCurrentVersion()
          CommonFeedbackSurveyService.feedbackSurveyDisableActionInvoked(feedbackSurveyConfig.surveyId)
        }
        feedbackSurveyConfig.getCancelNotificationAction(project)()
      }
    )
    notification.notify(project)
    if (!forTest) {
      CommonFeedbackSurveyService.feedbackSurveyShowed(feedbackSurveyConfig.surveyId)
      feedbackSurveyConfig.updateStateAfterNotificationShowed(project)
    }
  }

  private fun checkNumberShowsNotExceeded(): Boolean {
    return CommonFeedbackSurveyService.getNumberShowsOfFeedbackSurvey(
      feedbackSurveyConfig.surveyId) < MAX_FEEDBACK_SURVEY_NUMBER_SHOWS
  }
}