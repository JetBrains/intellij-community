// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.impl.statistics

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector
import com.intellij.platform.feedback.impl.state.CommonFeedbackSurveyService

class FeedbackSurveysStateCollector : ApplicationUsagesCollector() {

  private val GROUP = EventLogGroup("feedback.surveys.state", 1)
  private val NUMBER_FEEDBACK_SURVEY_SHOWS = GROUP.registerEvent(
    "number.of.notifications.shown",
    EventFields.StringValidatedByCustomRule("survey_id", FeedbackSurveyIdValidationRule::class.java),
    EventFields.Count
  )
  private val NUMBER_RESPOND_ACTION_INVOKED = GROUP.registerEvent(
    "number.of.respond.actions.invoked",
    EventFields.StringValidatedByCustomRule("survey_id", FeedbackSurveyIdValidationRule::class.java),
    EventFields.Count
  )
  private val NUMBER_DISABLE_ACTION_INVOKED = GROUP.registerEvent(
    "number.of.disable.actions.invoked",
    EventFields.StringValidatedByCustomRule("survey_id", FeedbackSurveyIdValidationRule::class.java),
    EventFields.Count
  )
  private val FEEDBACK_SURVEY_ANSWER_SENT = GROUP.registerEvent(
    "feedback.survey.answered",
    EventFields.StringValidatedByCustomRule("survey_id", FeedbackSurveyIdValidationRule::class.java),
  )

  override fun getGroup(): EventLogGroup = GROUP

  override fun getMetrics(): Set<MetricEvent> {
    val result: MutableSet<MetricEvent> = mutableSetOf()

    for (it in CommonFeedbackSurveyService.getNumberShowsForAllSurveys().entries) {
      result.add(NUMBER_FEEDBACK_SURVEY_SHOWS.metric(it.key, it.value))
    }

    for (it in CommonFeedbackSurveyService.getNumberRespondActionInvokedForAllSurveys().entries) {
      result.add(NUMBER_RESPOND_ACTION_INVOKED.metric(it.key, it.value))
    }

    for (it in CommonFeedbackSurveyService.getNumberDisableActionInvokedForAllSurveys().entries) {
      result.add(NUMBER_DISABLE_ACTION_INVOKED.metric(it.key, it.value))
    }

    for (it in CommonFeedbackSurveyService.getAllAnsweredFeedbackSurveys()) {
      result.add(FEEDBACK_SURVEY_ANSWER_SENT.metric(it))
    }

    return result
  }
}