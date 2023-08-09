// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.impl

import com.intellij.ide.BrowserUtil
import com.intellij.notification.NotificationAction
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.project.Project
import com.intellij.platform.feedback.*
import com.intellij.platform.feedback.impl.state.CommonFeedbackSurveyService
import com.intellij.platform.feedback.impl.state.DontShowAgainFeedbackService
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn


internal const val MAX_FEEDBACK_SURVEY_NUMBER_SHOWS: Int = 2

internal fun FeedbackSurveyConfig.checkIsFeedbackCollectionDeadlineNotPast(): Boolean {
  return Clock.System.todayIn(TimeZone.currentSystemDefault()) < lastDayOfFeedbackCollection
}

internal fun FeedbackSurveyConfig.checkIsIdeEAPIfRequired(): Boolean {
  if (requireIdeEAP) {
    return ApplicationInfoEx.getInstanceEx().isEAP
  }
  return true
}

internal fun showNotification(feedbackSurveyType: FeedbackSurveyType<*>, project: Project, forTest: Boolean) {
  val feedbackSurveyConfig = feedbackSurveyType.feedbackSurveyConfig
  val notification = feedbackSurveyConfig.createNotification(project, forTest)
  notification.addAction(
    NotificationAction.createSimpleExpiring(feedbackSurveyConfig.getRespondNotificationActionLabel()) {
      if (!forTest) {
        CommonFeedbackSurveyService.feedbackSurveyRespondActionInvoked(feedbackSurveyConfig.surveyId)
      }
      invokeRespondNotificationAction(feedbackSurveyType, project, forTest)
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


internal fun isSuitableToShow(feedbackSurveyConfig: FeedbackSurveyConfig, project: Project): Boolean {
  return !CommonFeedbackSurveyService.checkIsFeedbackSurveyAnswerSent(feedbackSurveyConfig.surveyId) &&
         feedbackSurveyConfig.checkIdeIsSuitable() &&
         feedbackSurveyConfig.checkIsFeedbackCollectionDeadlineNotPast() &&
         feedbackSurveyConfig.checkIsIdeEAPIfRequired() &&
         checkNumberShowsNotExceeded(feedbackSurveyConfig) &&
         feedbackSurveyConfig.checkExtraConditionSatisfied(project)
}

private fun invokeRespondNotificationAction(feedbackSurveyType: FeedbackSurveyType<*>, project: Project, forTest: Boolean) {
  when (feedbackSurveyType) {
    is InIdeFeedbackSurveyType -> {
      val inIdeFeedbackSurveyConfig = feedbackSurveyType.feedbackSurveyConfig as InIdeFeedbackSurveyConfig
      val dialog = inIdeFeedbackSurveyConfig.createFeedbackDialog(project, forTest)
      val isOk = dialog.showAndGet()
      if (isOk && !forTest) {
        inIdeFeedbackSurveyConfig.updateStateAfterDialogClosedOk(project)
        updateCommonFeedbackSurveysStateAfterSent(inIdeFeedbackSurveyConfig)
      }
    }
    is ExternalFeedbackSurveyType -> {
      val externalFeedbackSurveyConfig = feedbackSurveyType.feedbackSurveyConfig as ExternalFeedbackSurveyConfig
      browseToSurvey(project, externalFeedbackSurveyConfig)
      if (!forTest) {
        externalFeedbackSurveyConfig.updateStateAfterRespondActionInvoked(project)
        updateCommonFeedbackSurveysStateAfterSent(externalFeedbackSurveyConfig)
      }
    }
  }
}

private fun updateCommonFeedbackSurveysStateAfterSent(feedbackSurveyConfig: FeedbackSurveyConfig) {
  CommonFeedbackSurveyService.feedbackSurveyAnswerSent(feedbackSurveyConfig.surveyId)
}

private fun browseToSurvey(project: Project, feedbackSurveyConfig: ExternalFeedbackSurveyConfig) {
  BrowserUtil.browse(feedbackSurveyConfig.getUrlToSurvey(project), project)
}

private fun checkNumberShowsNotExceeded(feedbackSurveyConfig: FeedbackSurveyConfig): Boolean {
  return CommonFeedbackSurveyService.getNumberShowsOfFeedbackSurvey(
    feedbackSurveyConfig.surveyId) < MAX_FEEDBACK_SURVEY_NUMBER_SHOWS
}

