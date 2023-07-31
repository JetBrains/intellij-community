// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback

import com.intellij.openapi.project.Project

class InIdeFeedbackSurveyType<T : InIdeFeedbackSurveyConfig>(inIdeFeedbackActionConfig: T) : FeedbackSurveyType<T>() {

  override val feedbackSurveyConfig: T = inIdeFeedbackActionConfig

  override fun getRespondNotificationAction(project: Project, forTest: Boolean): () -> Unit {
    return {
      val dialog = feedbackSurveyConfig.createFeedbackDialog(project, forTest)
      val isOk = dialog.showAndGet()
      if (isOk && !forTest) {
        feedbackSurveyConfig.updateStateAfterDialogClosedOk(project)
        updateCommonFeedbackSurveysStateAfterSent()
      }
    }
  }
}
