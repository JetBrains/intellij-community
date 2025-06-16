// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.impl.state

import com.intellij.openapi.components.*
import com.intellij.platform.feedback.NotificationBasedFeedbackSurveyConfig
import kotlinx.serialization.Serializable

@Service(Service.Level.APP)
@State(name = "CommonFeedbackSurveyService", storages = [Storage("CommonFeedbackSurveyService.xml", roamingType = RoamingType.DISABLED)])
class CommonFeedbackSurveyService : PersistentStateComponent<CommonFeedbackSurveysState> {
  companion object {
    @JvmStatic
    fun getNumberShowsOfFeedbackSurvey(surveyId: String): Int {
      return getInstance().state.feedbackSurveyToNumberNotificationShows.getOrDefault(surveyId, 0)
    }

    @JvmStatic
    fun feedbackSurveyShowed(surveyId: String) {
      getInstance().state.feedbackSurveyToNumberNotificationShows.increment(surveyId)
    }

    @JvmStatic
    fun getNumberShowsForAllSurveys(): Map<String, Int> {
      return getInstance().state.feedbackSurveyToNumberNotificationShows
    }

    @JvmStatic
    fun feedbackSurveyRespondActionInvoked(surveyId: String) {
      getInstance().state.feedbackSurveyToNumberRespondActionInvoked.increment(surveyId)
    }

    @JvmStatic
    fun getNumberRespondActionInvokedForAllSurveys(): Map<String, Int> {
      return getInstance().state.feedbackSurveyToNumberRespondActionInvoked
    }

    @JvmStatic
    fun feedbackSurveyDisableActionInvoked(surveyId: String) {
      getInstance().state.feedbackSurveyToNumberDisableActionInvoked.increment(surveyId)
    }

    @JvmStatic
    fun getNumberDisableActionInvokedForAllSurveys(): Map<String, Int> {
      return getInstance().state.feedbackSurveyToNumberDisableActionInvoked
    }

    @JvmStatic
    fun feedbackSurveyAnswerSent(surveyId: String) {
      getInstance().state.answeredFeedbackSurveys.add(surveyId)
    }

    @JvmStatic
    fun checkIsFeedbackSurveyAnswerSent(surveyId: String): Boolean {
      return getInstance().state.answeredFeedbackSurveys.contains(surveyId)
    }

    @JvmStatic
    fun checkIsFeedbackSurveyAnswerSent(config: NotificationBasedFeedbackSurveyConfig): Boolean {
      if (config.isIndefinite) return false

      return checkIsFeedbackSurveyAnswerSent(config.surveyId)
    }

    @JvmStatic
    fun getAllAnsweredFeedbackSurveys(): Set<String> {
      return getInstance().state.answeredFeedbackSurveys
    }

    private fun getInstance(): CommonFeedbackSurveyService = service()

    private fun MutableMap<String, Int>.increment(key: String) {
      this[key] = this.getOrDefault(key, 0) + 1
    }
  }

  private var state: CommonFeedbackSurveysState = CommonFeedbackSurveysState()

  override fun getState(): CommonFeedbackSurveysState {
    return state
  }

  override fun loadState(state: CommonFeedbackSurveysState) {
    this.state = state
  }
}

@Serializable
data class CommonFeedbackSurveysState(
  val feedbackSurveyToNumberNotificationShows: MutableMap<String, Int> = mutableMapOf(),
  val feedbackSurveyToNumberRespondActionInvoked: MutableMap<String, Int> = mutableMapOf(),
  val feedbackSurveyToNumberDisableActionInvoked: MutableMap<String, Int> = mutableMapOf(),
  val answeredFeedbackSurveys: MutableSet<String> = mutableSetOf()
)