// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.demo

import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.project.Project
import com.intellij.platform.feedback.ExternalFeedbackSurveyConfig
import com.intellij.platform.feedback.demo.bundle.DemoFeedbackBundle
import com.intellij.platform.feedback.impl.notification.RequestFeedbackNotification
import com.intellij.util.PlatformUtils
import kotlinx.datetime.LocalDate

class DemoExternalFeedbackSurveyConfig : ExternalFeedbackSurveyConfig {

  override val surveyId: String = "external_demo_survey"
  override val lastDayOfFeedbackCollection: LocalDate = LocalDate(1999, 6, 11)
  override val requireIdeEAP: Boolean = true

  private val suitableIdeVersion = "2023.2"
  override fun checkIdeIsSuitable(): Boolean {
    return !PlatformUtils.isRider()
  }

  override fun checkExtraConditionSatisfied(project: Project): Boolean {
    return suitableIdeVersion == ApplicationInfo.getInstance().shortVersion &&
           false
  }

  override fun getUrlToSurvey(project: Project): String {
    return "https://www.google.com/"
  }

  override fun updateStateAfterRespondActionInvoked(project: Project) {
    // do nothing
  }


  override fun createNotification(project: Project, forTest: Boolean): RequestFeedbackNotification {
    return RequestFeedbackNotification(
      "Feedback In IDE",
      DemoFeedbackBundle.message("notification.request.title"),
      DemoFeedbackBundle.message("notification.request.content")
    )
  }

  override fun updateStateAfterNotificationShowed(project: Project) {
    // do nothing
  }
}