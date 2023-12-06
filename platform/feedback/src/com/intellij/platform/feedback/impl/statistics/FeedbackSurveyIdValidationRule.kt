// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.impl.statistics

import com.intellij.internal.statistic.eventLog.validator.ValidationResultType
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule
import com.intellij.internal.statistic.utils.getPluginInfoByDescriptor
import com.intellij.platform.feedback.impl.IdleFeedbackResolver

class FeedbackSurveyIdValidationRule : CustomValidationRule() {

  override fun getRuleId(): String {
    return "feedback_survey_id"
  }

  override fun doValidate(data: String, context: EventContext): ValidationResultType {
    val idleFeedbackSurveys = IdleFeedbackResolver.getJbIdleFeedbackSurveyExtensionList()
    val reportedFeedbackSurvey = idleFeedbackSurveys.find { it.getFeedbackSurveyId() == data } ?: return ValidationResultType.REJECTED
    val pluginDescriptor = reportedFeedbackSurvey.getPluginDescriptor() ?: return ValidationResultType.REJECTED
    val pluginInfo = getPluginInfoByDescriptor(pluginDescriptor)

    if (!pluginInfo.isDevelopedByJetBrains()) {
      return ValidationResultType.THIRD_PARTY
    }

    return ValidationResultType.ACCEPTED
  }
}