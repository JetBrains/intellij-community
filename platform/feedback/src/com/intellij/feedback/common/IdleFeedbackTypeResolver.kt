// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.feedback.common

import com.intellij.feedback.common.state.DontShowAgainFeedbackService
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import java.time.Duration
import java.time.LocalDateTime
import kotlin.random.Random

object IdleFeedbackTypeResolver {
  // 10 minutes
  private const val MIN_INACTIVE_TIME = 600

  private var lastActivityTime: LocalDateTime = LocalDateTime.now()

  fun checkActivity(project: Project?) {
    if (Duration.between(lastActivityTime, LocalDateTime.now()).toSeconds() >=
        Registry.intValue("platform.feedback.time.to.show.notification", MIN_INACTIVE_TIME)) {
      showFeedbackNotification(project)
    }
    lastActivityTime = LocalDateTime.now()
  }

  var isFeedbackNotificationDisabled: Boolean
    get() = DontShowAgainFeedbackService.getInstance().state.dontShowAgainIdeVersions
      .contains(ApplicationInfoEx.getInstanceEx().shortVersion)
    set(value) {
      if (value) {
        DontShowAgainFeedbackService.getInstance().state.dontShowAgainIdeVersions
          .add(ApplicationInfoEx.getInstanceEx().shortVersion)
      }
      else {
        DontShowAgainFeedbackService.getInstance().state.dontShowAgainIdeVersions
          .remove(ApplicationInfoEx.getInstanceEx().shortVersion)
      }
    }

  private fun showFeedbackNotification(project: Project?) {
    if (isFeedbackNotificationDisabled || !Registry.`is`("platform.feedback", true)) {
      return
    }
    val suitableFeedbackTypes = IdleFeedbackTypes.values().filter { it.isSuitable() }
    if (suitableFeedbackTypes.isEmpty()) {
      return
    }

    val feedbackIndex = Random.Default.nextInt(suitableFeedbackTypes.size)
    suitableFeedbackTypes[feedbackIndex].showNotification(project)
  }
}