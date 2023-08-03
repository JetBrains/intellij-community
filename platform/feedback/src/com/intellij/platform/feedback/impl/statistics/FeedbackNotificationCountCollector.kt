// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.impl.statistics

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.platform.feedback.impl.IdleFeedbackTypes

class FeedbackNotificationCountCollector : CounterUsagesCollector() {
  companion object {
    private val IDLE_FEEDBACK_TYPE = EventFields.Enum<IdleFeedbackTypes>("idle_feedback_type")
    private val GROUP = EventLogGroup("feedback.in.ide.notification", 5)

    private val REQUEST_NOTIFICATION_SHOWN = GROUP.registerEvent("notification.shown", IDLE_FEEDBACK_TYPE)
    private val RESPOND_NOTIFICATION_ACTION_INVOKED = GROUP.registerEvent("notification.respond.invoked", IDLE_FEEDBACK_TYPE)
    private val DISABLE_NOTIFICATION_ACTION_INVOKED = GROUP.registerEvent("notification.disable.invoked", IDLE_FEEDBACK_TYPE)

    fun logRequestNotificationShown(feedbackType: IdleFeedbackTypes) {
      REQUEST_NOTIFICATION_SHOWN.log(feedbackType)
    }

    fun logRespondNotificationActionInvoked(feedbackType: IdleFeedbackTypes) {
      RESPOND_NOTIFICATION_ACTION_INVOKED.log(feedbackType)
    }

    fun logDisableNotificationActionInvoked(feedbackType: IdleFeedbackTypes) {
      DISABLE_NOTIFICATION_ACTION_INVOKED.log(feedbackType)
    }
  }

  override fun getGroup(): EventLogGroup = GROUP
}