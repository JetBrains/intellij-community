// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.feedback.statistics

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector

class ProjectCreationFeedbackCountCollector : CounterUsagesCollector() {

  companion object {
    private val GROUP = EventLogGroup("feedback.project.creation", 1)

    private val REQUEST_NOTIFICATION_SHOWED = GROUP.registerEvent("request.notification.showed")
    private val REQUEST_NOTIFICATION_ACTION_CALLED = GROUP.registerEvent("request.notification.action.called")
    private val DIALOG_SHOWED = GROUP.registerEvent("dialog.showed")
    private val DIALOG_CANCELED = GROUP.registerEvent("dialog.canceled")
    private val DIALOG_CLOSED = GROUP.registerEvent("dialog.closed")
    private val FEEDBACK_ATTEMPT_TO_SEND = GROUP.registerEvent("feedback.attempt.send")
    private val FEEDBACK_SENT_SUCCESSFULLY = GROUP.registerEvent("feedback.sent.successfully")
    private val FEEDBACK_SENT_ERROR = GROUP.registerEvent("feedback.sent.error")

    fun logNotificationShowed() {
      REQUEST_NOTIFICATION_SHOWED.log()
    }

    fun logNotificationActionCalled() {
      REQUEST_NOTIFICATION_ACTION_CALLED.log()
    }

    fun logDialogShowed() {
      DIALOG_SHOWED.log()
    }

    fun logDialogCanceled() {
      DIALOG_CANCELED.log()
    }

    fun logDialogClosed() {
      DIALOG_CLOSED.log()
    }

    fun logFeedbackAttemptToSend() {
      FEEDBACK_ATTEMPT_TO_SEND.log()
    }

    fun logFeedbackSentSuccessfully() {
      FEEDBACK_SENT_SUCCESSFULLY.log()
    }

    fun logFeedbackSentError() {
      FEEDBACK_SENT_ERROR.log()
    }
  }

  override fun getGroup(): EventLogGroup {
    return GROUP
  }
}