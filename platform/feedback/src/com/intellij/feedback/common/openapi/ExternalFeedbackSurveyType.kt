// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.feedback.common.openapi

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.project.Project

class ExternalFeedbackSurveyType<T : ExternalFeedbackSurveyConfig>(externalFeedbackActionConfig: T) : FeedbackSurveyType<T>() {

  override val feedbackSurveyConfig: T = externalFeedbackActionConfig
  override fun getRespondNotificationAction(project: Project, forTest: Boolean): () -> Unit {
    return {
      browseToSurvey(project)
      if (!forTest) {
        feedbackSurveyConfig.updateStateAfterRespondActionInvoked(project)
        updateCommonFeedbackSurveysStateAfterSent()
      }
    }
  }

  private fun browseToSurvey(project: Project) {
    BrowserUtil.browse(feedbackSurveyConfig.getUrlToSurvey(project), project)
  }
}