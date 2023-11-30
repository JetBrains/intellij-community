// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.impl

import com.intellij.internal.statistic.utils.getPluginInfoByDescriptor
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.feedback.FeedbackSurvey
import com.intellij.platform.feedback.impl.state.DontShowAgainFeedbackService
import kotlin.random.Random

@Service(Service.Level.APP)
class IdleFeedbackResolver {
  companion object {
    @JvmStatic
    internal fun getInstance(): IdleFeedbackResolver = service()

    private val IDLE_FEEDBACK_SURVEY = ExtensionPointName<FeedbackSurvey>("com.intellij.feedback.idleFeedbackSurvey")

    internal fun getJbIdleFeedbackSurveyExtensionList(): List<FeedbackSurvey> {
      return IDLE_FEEDBACK_SURVEY.extensionList.filter {
        val pluginDescriptor = it.getPluginDescriptor() ?: return@filter false
        val pluginInfo = getPluginInfoByDescriptor(pluginDescriptor)
        pluginInfo.isDevelopedByJetBrains()
      }
    }
  }

  internal fun showFeedbackNotification(project: Project?) {
    if (!DontShowAgainFeedbackService.checkIsAllowedToShowFeedback() ||
        !Registry.`is`("platform.feedback", true) ||
        project == null) {
      return
    }

    val suitableFeedbackTypes = IdleFeedbackTypes.values().filter { it.isSuitable() }
    val suitableIdleFeedbackSurveys = getJbIdleFeedbackSurveyExtensionList().filter { it.isSuitableToShow(project) }

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