// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.feedback.demo

import com.intellij.feedback.common.openapi.FeedbackSurvey
import com.intellij.feedback.common.openapi.InIdeFeedbackSurveyConfig
import com.intellij.feedback.common.openapi.InIdeFeedbackSurveyType

class DemoInIdeFeedbackSurvey : FeedbackSurvey() {

  override val feedbackSurveyType: InIdeFeedbackSurveyType<InIdeFeedbackSurveyConfig> =
    InIdeFeedbackSurveyType(DemoFeedbackSurveyConfig(surveyId))
}