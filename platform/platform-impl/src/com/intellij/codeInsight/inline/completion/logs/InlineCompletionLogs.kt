// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.logs

import com.intellij.codeInsight.inline.completion.InlineCompletionEventAdapter
import com.intellij.codeInsight.inline.completion.InlineCompletionEventType
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields.createAdditionalDataField
import com.intellij.internal.statistic.eventLog.events.ObjectEventField
import com.intellij.internal.statistic.eventLog.events.VarargEventId
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.editor.Editor

object InlineCompletionLogs : CounterUsagesCollector() {
  val GROUP = EventLogGroup("inline.completion.v2", 1, recorder = "ML")

  override fun getGroup(): EventLogGroup = GROUP

  object Session {
    private const val SESSION_EVENT_ID = "session"

    private val ADDITIONAL: ObjectEventField = createAdditionalDataField(GROUP.id, SESSION_EVENT_ID)

    val SESSION_EVENT: VarargEventId = GROUP.registerVarargEvent(
      SESSION_EVENT_ID,
      ADDITIONAL,
    )
  }

  class Listener : InlineCompletionEventAdapter {
    private var editor: Editor? = null

    override fun onRequest(event: InlineCompletionEventType.Request) {
      InlineCompletionLogsContainer.create(event.request.editor)
      editor = event.request.editor
    }

    override fun onHide(event: InlineCompletionEventType.Hide) {
      val curEditor = editor ?: return
      val container = InlineCompletionLogsContainer.get(curEditor)
      container.log() // TODO move from EDT to background
    }
  }
}
