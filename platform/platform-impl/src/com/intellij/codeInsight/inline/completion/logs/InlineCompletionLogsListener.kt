// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.logs

import com.intellij.codeInsight.inline.completion.InlineCompletionEventAdapter
import com.intellij.codeInsight.inline.completion.InlineCompletionEventType
import com.intellij.codeInsight.inline.completion.logs.FinishingLogs.FINISH_TYPE
import com.intellij.codeInsight.inline.completion.logs.FinishingLogs.TIME_TO_START_SHOWING
import com.intellij.codeInsight.inline.completion.logs.FinishingLogs.WAS_SHOWN
import com.intellij.codeInsight.inline.completion.logs.InlineCompletionLogsContainer.Phase
import com.intellij.codeInsight.inline.completion.logs.StartingLogs.REQUEST_ID
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.Editor
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

internal class InlineCompletionLogsListener(private val editor: Editor) : InlineCompletionEventAdapter {
  private val lastInvocationTimestamp = AtomicLong()
  private val wasShown = AtomicBoolean()

  override fun onRequest(event: InlineCompletionEventType.Request) {
    lastInvocationTimestamp.set(event.lastInvocation)
    wasShown.set(false)
    val container = InlineCompletionLogsContainer.create(event.request.editor)
    container.add(REQUEST_ID with event.request.requestId)
    container.addAsync {
      readAction {
        InlineCompletionContextLogs.getFor(event.request)
      }
    }
  }

  override fun onShow(event: InlineCompletionEventType.Show) {
    if (wasShown.getAndSet(true)) return
    val container = InlineCompletionLogsContainer.get(editor) ?: return
    container.add(TIME_TO_START_SHOWING with (System.currentTimeMillis() - lastInvocationTimestamp.get()))
  }

  override fun onHide(event: InlineCompletionEventType.Hide) {
    val container = InlineCompletionLogsContainer.remove(editor) ?: return
    container.add(WAS_SHOWN with wasShown.get())
    container.add(FINISH_TYPE with event.finishType)
    InlineCompletionLogsScopeProvider.getInstance().cs.launch {
      container.log()
    }
  }
}

private object StartingLogs : PhasedLogs(Phase.INLINE_API_STARTING) {
  val REQUEST_ID = register(EventFields.Long("request_id"))
}

private object FinishingLogs : PhasedLogs(Phase.INLINE_API_FINISHING) {
  val WAS_SHOWN = register(EventFields.Boolean("was_shown"))
  val TIME_TO_START_SHOWING = register(EventFields.Long("time_to_start_showing"))
  val FINISH_TYPE = register(EventFields.Enum("finish_type", InlineCompletionUsageTracker.ShownEvents.FinishType::class.java))
}

internal class InlineCompletionListenerSessionLogs : InlineCompletionSessionLogsEP {
  override val fields = listOf(StartingLogs, FinishingLogs)
}