// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.impl

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.feedback.FeedbackSurvey
import com.intellij.platform.feedback.impl.state.DontShowAgainFeedbackService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.reflect.KClass

@Service(Service.Level.APP)
class OnDemandFeedbackResolver(private val cs: CoroutineScope) {
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
   * Shows [surveyClass] if it's suitable and user allows showing surveys.
   *
   * @param onShowAction Callback action that is executed after the show operation is attempted.
   * Accepts a boolean where true indicates the notification was shown and false indicates it was not.
   * Invokes in the background.
   *
   */
  fun <S : FeedbackSurvey> showFeedbackNotification(surveyClass: KClass<S>, project: Project, onShowAction: (Boolean) -> Unit) {
    val survey = getJbOnDemandFeedbackSurveyExtension(surveyClass)

    cs.launch {
      if (!canShowFeedbackNotification() || !survey.isSuitableToShow(project)) {
        onShowAction.invoke(false)
        return@launch
      }

      withContext(Dispatchers.EDT) {
        survey.showNotification(project)
      }
      onShowAction.invoke(true)
    }
  }
}