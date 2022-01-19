// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.feedback

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import java.time.Duration
import java.time.LocalDateTime
import kotlin.random.Random

object FeedbackTypeResolver {
  // 10 minutes
  private const val MIN_INACTIVE_TIME = 10

  var lastActivityTime: LocalDateTime = LocalDateTime.now()
    private set

  fun checkActivity(project: Project?) {
    if (Duration.between(LocalDateTime.now(), lastActivityTime).toMinutes() >= MIN_INACTIVE_TIME) {
      showFeedbackNotification(project)
    }
    lastActivityTime = LocalDateTime.now()
  }

  //TODO: Make it persistence for minor versions
  private const val isFeedbackNotificationEnabledPropertyName = "isProjectCreationNotificationEnabled"
  var isFeedbackNotificationEnabled
    get() = PropertiesComponent.getInstance().getBoolean(isFeedbackNotificationEnabledPropertyName, true)
    set(value) {
      PropertiesComponent.getInstance().setValue(isFeedbackNotificationEnabledPropertyName, value)
    }

  private fun showFeedbackNotification(project: Project?) {
    if (!Registry.`is`("platform.feedback", false) || !isFeedbackNotificationEnabled) {
      return
    }
    val suitableFeedbackTypes = FeedbackTypes.values().filter { it.isSuitable() }
    if (suitableFeedbackTypes.isEmpty()) {
      return
    }

    val feedbackIndex = Random.Default.nextInt(suitableFeedbackTypes.size)
    suitableFeedbackTypes[feedbackIndex].showNotification(project)
  }
}