// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.feedback.common.state

import com.intellij.openapi.components.*
import kotlinx.serialization.Serializable

@Service(Service.Level.APP)
@State(name = "CommonFeedbackSurveyService", storages = [Storage("CommonFeedbackSurveyService.xml")])
class CommonFeedbackSurveyService : PersistentStateComponent<CommonFeedbackSurveysState> {
  companion object {

    @JvmStatic
    fun getNumberShowsOfFeedbackSurvey(surveyId: String): Int {
      return getInstance().state.feedbackSurveyToNumberShows.getOrDefault(surveyId, 0)
    }

    @JvmStatic
    fun feedbackSurveyShowed(surveyId: String) {
      getInstance().state.feedbackSurveyToNumberShows.increment(surveyId)
    }

    @JvmStatic
    fun feedbackSurveySent(surveyId: String) {
      getInstance().state.sentFeedbackSurveys.add(surveyId)
    }

    fun checkIsFeedbackSurveySent(surveyId: String): Boolean {
      return getInstance().state.sentFeedbackSurveys.contains(surveyId)
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
  val feedbackSurveyToNumberShows: MutableMap<String, Int> = mutableMapOf(),
  val sentFeedbackSurveys: MutableSet<String> = mutableSetOf()
)