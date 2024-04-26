// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.impl

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.feedback.FeedbackSurvey
import com.intellij.platform.feedback.impl.state.DontShowAgainFeedbackService
import kotlin.reflect.KClass

@Service(Service.Level.APP)
class OnDemandFeedbackResolver {
  companion object {
    @JvmStatic
    fun getInstance(): OnDemandFeedbackResolver = service()

    private fun <S : FeedbackSurvey> getJbOnDemandFeedbackSurveyExtension(surveyClass: KClass<S>): S {
      return IdleFeedbackResolver.getJbIdleFeedbackSurveyExtensionList().filterIsInstance(surveyClass.java).first()
    }

    private fun canShowFeedbackNotification(): Boolean {
      return DontShowAgainFeedbackService.checkIsAllowedToShowFeedback() && Registry.`is`("platform.feedback", true)
    }
  }

  /**
   * Shows [survey] if it's suitable and user allows showing surveys.
   *
   * @return `true` if a survey notification was shown, `false` otherwise
   */
  fun <S : FeedbackSurvey> showFeedbackNotification(surveyClass: KClass<S>, project: Project): Boolean {
    val survey = getJbOnDemandFeedbackSurveyExtension(surveyClass)
    if (!canShowFeedbackNotification()) return false
    if (!survey.isSuitableToShow(project)) return false
    survey.showNotification(project)
    return true
  }
}