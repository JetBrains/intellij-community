// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.feedback.common.statistics

import com.intellij.feedback.common.state.CommonFeedbackSurveyService
import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector

class FeedbackSurveysStateCollector : ApplicationUsagesCollector() {

  companion object {
    private val GROUP = EventLogGroup("feedback.surveys.state", 1)

    private val NUMBER_FEEDBACK_SURVEY_SHOWS = GROUP.registerEvent(
      "numberNotificationShows",
      EventFields.StringValidatedByInlineRegexp("surveyId", ".+"),
      EventFields.Int("numberOfShows")
    )

    private val NUMBER_RESPOND_ACTION_INVOKED = GROUP.registerEvent(
      "numberRespondActionInvoked",
      EventFields.StringValidatedByInlineRegexp("surveyId", ".+"),
      EventFields.Int("numberOfInvocations")
    )

    private val NUMBER_DISABLE_ACTION_INVOKED = GROUP.registerEvent(
      "numberDisableActionInvoked",
      EventFields.StringValidatedByInlineRegexp("surveyId", ".+"),
      EventFields.Int("numberOfInvocations")
    )

    private val FEEDBACK_SURVEY_ANSWER_SENT = GROUP.registerEvent(
      "answeredFeedbackSurvey",
      EventFields.StringValidatedByInlineRegexp("surveyId", ".+")
    )

  }

  override fun getGroup(): EventLogGroup = GROUP

  override fun getMetrics(): Set<MetricEvent> {
    val result: MutableSet<MetricEvent> = mutableSetOf()

    CommonFeedbackSurveyService.getNumberShowsForAllSurveys().entries.forEach {
      result.add(NUMBER_FEEDBACK_SURVEY_SHOWS.metric(it.key, it.value))
    }

    CommonFeedbackSurveyService.getNumberRespondActionInvokedForAllSurveys().entries.forEach {
      result.add(NUMBER_RESPOND_ACTION_INVOKED.metric(it.key, it.value))
    }

    CommonFeedbackSurveyService.getNumberDisableActionInvokedForAllSurveys().entries.forEach {
      result.add(NUMBER_DISABLE_ACTION_INVOKED.metric(it.key, it.value))
    }

    CommonFeedbackSurveyService.getAllAnsweredFeedbackSurveys().forEach {
      result.add(FEEDBACK_SURVEY_ANSWER_SENT.metric(it))
    }

    return result
  }
}