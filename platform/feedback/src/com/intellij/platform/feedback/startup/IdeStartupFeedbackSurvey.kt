// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.startup

import com.intellij.platform.feedback.FeedbackSurvey
import com.intellij.platform.feedback.FeedbackSurveyType
import com.intellij.platform.feedback.InIdeFeedbackSurveyType

class IdeStartupFeedbackSurvey : FeedbackSurvey() {
  override val feedbackSurveyType: FeedbackSurveyType<*> = InIdeFeedbackSurveyType(IdeStartupFeedbackConfigSurvey())
}