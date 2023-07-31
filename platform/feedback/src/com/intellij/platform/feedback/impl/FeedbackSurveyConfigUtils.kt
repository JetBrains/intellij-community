// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.impl

import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.platform.feedback.FeedbackSurveyConfig
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

fun FeedbackSurveyConfig.checkIsFeedbackCollectionDeadlineNotPast(): Boolean {
  return Clock.System.todayIn(TimeZone.currentSystemDefault()) < lastDayOfFeedbackCollection
}

fun FeedbackSurveyConfig.checkIsIdeEAPIfRequired(): Boolean {
  if (requireIdeEAP) {
    return ApplicationInfoEx.getInstanceEx().isEAP
  }
  return true
}