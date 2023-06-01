// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.feedback.common

import com.intellij.feedback.common.openapi.FeedbackSurvey
import com.intellij.feedback.common.state.DontShowAgainFeedbackService
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import kotlin.random.Random

@Service(Service.Level.APP)
class IdleFeedbackResolver {
  companion object {
    @JvmStatic
    fun getInstance(): IdleFeedbackResolver = service()
  }

  fun showFeedbackNotification(project: Project?) {
    if (!DontShowAgainFeedbackService.checkIsAllowedToShowFeedback() ||
        !Registry.`is`("platform.feedback", true) ||
        project == null) {
      return
    }

    val suitableFeedbackTypes = IdleFeedbackTypes.values().filter { it.isSuitable() }
    val suitableIdleFeedbackSurveys = FeedbackSurvey.getJBExtensionList().filter { it.isSuitableToShow(project) }

    if (suitableFeedbackTypes.isEmpty() && suitableIdleFeedbackSurveys.isEmpty()) {
      return
    }

    val feedbackIndex = Random.Default.nextInt(suitableFeedbackTypes.size + suitableIdleFeedbackSurveys.size)

    if (feedbackIndex < suitableFeedbackTypes.size) {
      suitableFeedbackTypes[feedbackIndex].showNotification(project)
    }
    else {
      suitableIdleFeedbackSurveys[feedbackIndex].showNotification(project)
    }
  }
}