// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.impl

import com.intellij.idea.AppMode
import com.intellij.internal.statistic.utils.getPluginInfoByDescriptor
import com.intellij.notification.NotificationsManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.feedback.FeedbackSurvey
import com.intellij.platform.feedback.impl.notification.RequestFeedbackNotification
import com.intellij.platform.feedback.impl.state.DontShowAgainFeedbackService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

@Service(Service.Level.APP)
class IdleFeedbackResolver(private val cs: CoroutineScope) {
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

    if (AppMode.isRemoteDevHost()) {
      thisLogger().debug("Not showing idle feedback notification on remote-dev host")
      return
    }

    val feedbackNotifications = NotificationsManager.getNotificationsManager()
      .getNotificationsOfType(RequestFeedbackNotification::class.java, project)
    if (feedbackNotifications.isNotEmpty()) {
      thisLogger().debug("There is already another request feedback notification shown, not showing more feedback notifications")
      return
    }

    cs.launch {
      val suitableIdleFeedbackSurveys = getJbIdleFeedbackSurveyExtensionList().filter { it.isSuitableToShow(project) }

      if (suitableIdleFeedbackSurveys.isEmpty()) {
        return@launch
      }

      val feedbackIndex = Random.nextInt(suitableIdleFeedbackSurveys.size)
      withContext(Dispatchers.EDT) {
        suitableIdleFeedbackSurveys[feedbackIndex].showNotification(project)
      }
    }
  }
}