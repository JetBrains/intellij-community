// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.feedback.common.statistics

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector

class FeedbackNotificationCountCollector : CounterUsagesCollector() {
  companion object {
    private val GROUP = EventLogGroup("feedback.in.ide.notification", 1)

    private val REQUEST_NOTIFICATION_SHOWN = GROUP.registerEvent(
      "notification.shown", EventFields.StringValidatedByInlineRegexp("feedback_id", "[\\w_ -]*")
    )
    private val RESPOND_NOTIFICATION_ACTION_INVOKED = GROUP.registerEvent(
      "notification.respond.invoked", EventFields.StringValidatedByInlineRegexp("feedback_id", "[\\w_ -]*")
    )
    private val DISABLE_NOTIFICATION_ACTION_INVOKED = GROUP.registerEvent(
      "notification.disable.invoked", EventFields.StringValidatedByInlineRegexp("feedback_id", "[\\w_ -]*")
    )

    fun logRequestNotificationShown(feedbackId: String) {
      REQUEST_NOTIFICATION_SHOWN.log(feedbackId)
    }

    fun logRespondNotificationActionInvoked(feedbackId: String) {
      RESPOND_NOTIFICATION_ACTION_INVOKED.log(feedbackId)
    }

    fun logDisableNotificationActionInvoked(feedbackId: String) {
      DISABLE_NOTIFICATION_ACTION_INVOKED.log(feedbackId)
    }
  }

  override fun getGroup(): EventLogGroup = GROUP
}