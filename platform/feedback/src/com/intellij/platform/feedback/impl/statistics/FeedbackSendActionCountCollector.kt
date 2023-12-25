// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.impl.statistics

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector

internal object FeedbackSendActionCountCollector : CounterUsagesCollector() {
  internal val GROUP = EventLogGroup("feedback.in.ide.action.send", 1)

  private val FEEDBACK_SEND_SUCCESS = GROUP.registerEvent("success")
  private val FEEDBACK_SEND_FAIL = GROUP.registerEvent("fail")

  internal fun logFeedbackSendSuccess() {
    FEEDBACK_SEND_SUCCESS.log()
  }

  internal fun logFeedbackSendFail() {
    FEEDBACK_SEND_FAIL.log()
  }

  override fun getGroup(): EventLogGroup = GROUP
}