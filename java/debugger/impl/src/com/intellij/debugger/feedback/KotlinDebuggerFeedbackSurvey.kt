// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.feedback

import com.intellij.platform.feedback.FeedbackSurvey
import com.intellij.platform.feedback.InIdeFeedbackSurveyType


internal class KotlinDebuggerFeedbackSurvey : FeedbackSurvey() {
  override val feedbackSurveyType: InIdeFeedbackSurveyType<KotlinDebuggerFeedbackSurveyConfig> =
    InIdeFeedbackSurveyType(KotlinDebuggerFeedbackSurveyConfig())
}