// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.logs

import com.intellij.codeInsight.inline.completion.InlineCompletionEvent
import com.intellij.codeInsight.inline.completion.InlineCompletionEventAdapter
import com.intellij.codeInsight.inline.completion.InlineCompletionEventType
import com.intellij.codeInsight.inline.completion.logs.FinishingLogs.FINISH_TYPE
import com.intellij.codeInsight.inline.completion.logs.FinishingLogs.FULL_INSERT_ACTIONS
import com.intellij.codeInsight.inline.completion.logs.FinishingLogs.INVALIDATION_EVENT
import com.intellij.codeInsight.inline.completion.logs.FinishingLogs.NEXT_LINE_ACTIONS
import com.intellij.codeInsight.inline.completion.logs.FinishingLogs.NEXT_WORD_ACTIONS
import com.intellij.codeInsight.inline.completion.logs.FinishingLogs.SHOWING_TIME
import com.intellij.codeInsight.inline.completion.logs.FinishingLogs.TIME_TO_START_SHOWING
import com.intellij.codeInsight.inline.completion.logs.FinishingLogs.TOTAL_INSERTED_LENGTH
import com.intellij.codeInsight.inline.completion.logs.FinishingLogs.TOTAL_INSERTED_LINES
import com.intellij.codeInsight.inline.completion.logs.FinishingLogs.WAS_SHOWN
import com.intellij.codeInsight.inline.completion.logs.InlineCompletionLogsContainer.Phase
import com.intellij.codeInsight.inline.completion.logs.StartingLogs.FILE_LANGUAGE
import com.intellij.codeInsight.inline.completion.logs.StartingLogs.INLINE_API_PROVIDER
import com.intellij.codeInsight.inline.completion.logs.StartingLogs.REQUEST_EVENT
import com.intellij.codeInsight.inline.completion.logs.StartingLogs.REQUEST_ID
import com.intellij.codeInsight.inline.completion.session.InlineCompletionContext
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.Editor
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

internal class InlineCompletionLogsListener(private val editor: Editor) : InlineCompletionEventAdapter {
  private val lastInvocationTimestamp = AtomicLong()
  private val showStartTime = AtomicLong()
  private val wasShown = AtomicBoolean()
  private val fullInsertActions = AtomicInteger()
  private val nextWordActions = AtomicInteger()
  private val nextLineActions = AtomicInteger()
  private val totalInsertedLength = AtomicInteger()
  private val totalInsertedLines = AtomicInteger()


  override fun onRequest(event: InlineCompletionEventType.Request) {
    lastInvocationTimestamp.set(event.lastInvocation)
    wasShown.set(false)
    showStartTime.set(0)
    nextWordActions.set(0)
    nextLineActions.set(0)
    fullInsertActions.set(0)
    totalInsertedLength.set(0)
    totalInsertedLines.set(0)
    val container = InlineCompletionLogsContainer.create(event.request.editor)
    container.add(REQUEST_ID with event.request.requestId)
    container.add(REQUEST_EVENT with event.request.event.javaClass)
    container.add(INLINE_API_PROVIDER with event.provider)
    event.request.event.toRequest()?.file?.language?.let { container.add(FILE_LANGUAGE with it) }
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
    showStartTime.set(System.currentTimeMillis())
  }

  override fun onInsert(event: InlineCompletionEventType.Insert) {
    val textToInsert = InlineCompletionContext.getOrNull(editor)?.textToInsert() ?: return
    totalInsertedLength.addAndGet(textToInsert.length)
    totalInsertedLines.addAndGet(textToInsert.lines().size)
    fullInsertActions.incrementAndGet()
  }

  override fun onChange(event: InlineCompletionEventType.Change) {
    when (event.event) {
      is InlineCompletionEvent.InsertNextWord -> {
        totalInsertedLength.addAndGet(event.lengthChange)
        nextWordActions.incrementAndGet()
      }
      is InlineCompletionEvent.InsertNextLine -> {
        totalInsertedLength.addAndGet(event.lengthChange)
        totalInsertedLines.incrementAndGet()
        nextLineActions.incrementAndGet()
      }
    }
  }

  override fun onInvalidated(event: InlineCompletionEventType.Invalidated) {
    val container = InlineCompletionLogsContainer.get(editor) ?: return
    container.add(INVALIDATION_EVENT.with(event.event.javaClass))
  }

  override fun onHide(event: InlineCompletionEventType.Hide) {
    val container = InlineCompletionLogsContainer.remove(editor) ?: return
    container.add(WAS_SHOWN with wasShown.get())
    container.add(SHOWING_TIME.with(System.currentTimeMillis() - showStartTime.get()))
    container.add(FINISH_TYPE with event.finishType)
    container.add(FULL_INSERT_ACTIONS with fullInsertActions.get())
    container.add(NEXT_WORD_ACTIONS with nextWordActions.get())
    container.add(NEXT_LINE_ACTIONS with nextLineActions.get())
    container.add(TOTAL_INSERTED_LENGTH with totalInsertedLength.get())
    container.add(TOTAL_INSERTED_LINES with totalInsertedLines.get())
    container.logCurrent() // see doc of this function, it's very fast, and we should wait for its completion
  }
}

private object StartingLogs : PhasedLogs(Phase.INLINE_API_STARTING) {
  val REQUEST_ID = register(EventFields.Long("request_id"))
  val REQUEST_EVENT = register(EventFields.Class("request_event"))
  val INLINE_API_PROVIDER = register(EventFields.Class("inline_api_provider"))
  val FILE_LANGUAGE = register(EventFields.Language("file_language"))
}

private object FinishingLogs : PhasedLogs(Phase.INLINE_API_FINISHING) {
  val WAS_SHOWN = register(EventFields.Boolean("was_shown", "Indicates whether completion or some part of it was shown during the session or not"))
  val TIME_TO_START_SHOWING = register(EventFields.Long("time_to_start_showing", "Time from the completion request to start showing at least one element"))
  val SHOWING_TIME = register(EventFields.Long("showing_time", "Duration from the beginning of the show to its end (for any reason)"))
  val FINISH_TYPE = register(EventFields.Enum("finish_type", InlineCompletionUsageTracker.ShownEvents.FinishType::class.java, "Indicates how completion session was finished"))
  val INVALIDATION_EVENT = register(EventFields.Class("invalidation_event", "In case of finish type 'invalidated'  which exactly event invalidated the completion"))
  val FULL_INSERT_ACTIONS = register(EventFields.Int("full_insert_actions", "Number of full inline completion inserts"))
  val NEXT_WORD_ACTIONS = register(EventFields.Int("next_word_actions", "Number of next word inline completion inserts"))
  val NEXT_LINE_ACTIONS = register(EventFields.Int("next_line_actions", "Number of next line inline completion inserts"))
  val TOTAL_INSERTED_LENGTH = register(EventFields.Int("total_inserted_length", "Total length of inserted text"))
  val TOTAL_INSERTED_LINES = register(EventFields.Int("total_inserted_lines", "Total number of inserted lines"))
}

internal class InlineCompletionListenerSessionLogs : InlineCompletionSessionLogsEP {
  override val fields = listOf(StartingLogs, FinishingLogs)
}