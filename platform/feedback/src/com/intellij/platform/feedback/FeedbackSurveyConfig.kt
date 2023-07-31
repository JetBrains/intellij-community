// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback

import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.feedback.impl.bundle.CommonFeedbackBundle
import com.intellij.platform.feedback.impl.notification.RequestFeedbackNotification
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

interface FeedbackSurveyConfig {

  val surveyId: String
  val lastDayOfFeedbackCollection: LocalDate
  val requireIdeEAP: Boolean

  fun checkIdeIsSuitable(): Boolean

  fun checkExtraConditionSatisfied(project: Project): Boolean

  fun createNotification(project: Project, forTest: Boolean): RequestFeedbackNotification

  fun updateStateAfterNotificationShowed(project: Project)

  @NlsSafe
  fun getRespondNotificationActionLabel(): String {
    return CommonFeedbackBundle.message("notification.request.feedback.action.respond.text")
  }

  @NlsSafe
  fun getCancelNotificationActionLabel(): String {
    return CommonFeedbackBundle.message("notification.request.feedback.action.dont.show.text")
  }

  fun getCancelNotificationAction(project: Project): () -> Unit {
    return {}
  }

}

fun FeedbackSurveyConfig.checkIsFeedbackCollectionDeadlineNotPast(): Boolean {
  return Clock.System.todayIn(TimeZone.currentSystemDefault()) < lastDayOfFeedbackCollection
}

fun FeedbackSurveyConfig.checkIsIdeEAPIfRequired(): Boolean {
  if (requireIdeEAP) {
    return ApplicationInfoEx.getInstanceEx().isEAP
  }
  return true
}