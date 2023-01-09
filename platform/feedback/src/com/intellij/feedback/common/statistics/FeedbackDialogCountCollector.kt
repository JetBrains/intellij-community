// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.feedback.common.statistics

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector

class FeedbackDialogCountCollector : CounterUsagesCollector() {
  companion object {
    private val GROUP = EventLogGroup("feedback.in.ide.dialog", 1)

    private val DIALOG_SHOWN = GROUP.registerEvent(
      "dialog.shown", EventFields.StringValidatedByInlineRegexp("feedback_id", "[\\w_ -]*")
    )
    private val DIALOG_OK_ACTION = GROUP.registerEvent(
      "dialog.ok", EventFields.StringValidatedByInlineRegexp("feedback_id", "[\\w_ -]*")
    )
    private val DIALOG_CANCEL_ACTION = GROUP.registerEvent(
      "dialog.cancel", EventFields.StringValidatedByInlineRegexp("feedback_id", "[\\w_ -]*")
    )

    fun logDialogShown(feedbackId: String) {
      DIALOG_SHOWN.log(feedbackId)
    }

    fun logDialogOkAction(feedbackId: String) {
      DIALOG_OK_ACTION.log(feedbackId)
    }

    fun logDialogCancelAction(feedbackId: String) {
      DIALOG_CANCEL_ACTION.log(feedbackId)
    }

  }

  override fun getGroup(): EventLogGroup = GROUP
}