// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.demo

import com.intellij.platform.feedback.FeedbackSurvey
import com.intellij.platform.feedback.InIdeFeedbackSurveyConfig
import com.intellij.platform.feedback.InIdeFeedbackSurveyType

class DemoInIdeFeedbackSurvey : FeedbackSurvey() {

  override val feedbackSurveyType: InIdeFeedbackSurveyType<InIdeFeedbackSurveyConfig> =
    InIdeFeedbackSurveyType(DemoInIdeFeedbackSurveyConfig())
}