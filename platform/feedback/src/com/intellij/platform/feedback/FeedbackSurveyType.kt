// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback

import com.intellij.openapi.project.Project
import com.intellij.platform.feedback.impl.isSuitableToShow
import com.intellij.platform.feedback.impl.showNotification

sealed class FeedbackSurveyType<T : FeedbackSurveyConfig> {

  internal abstract val feedbackSurveyConfig: T

  internal fun getFeedbackSurveyId(): String {
    return feedbackSurveyConfig.surveyId
  }

  internal fun isSuitableToShow(project: Project): Boolean {
    return isSuitableToShow(feedbackSurveyConfig, project)
  }

  internal fun showNotification(project: Project, forTest: Boolean) {
    showNotification(this, project, forTest)
  }
}