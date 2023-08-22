// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.demo

import com.intellij.platform.feedback.ExternalFeedbackSurveyConfig
import com.intellij.platform.feedback.ExternalFeedbackSurveyType
import com.intellij.platform.feedback.FeedbackSurvey

class DemoExternalFeedbackSurvey : FeedbackSurvey() {

  override val feedbackSurveyType: ExternalFeedbackSurveyType<ExternalFeedbackSurveyConfig> =
    ExternalFeedbackSurveyType(DemoExternalFeedbackSurveyConfig())
}