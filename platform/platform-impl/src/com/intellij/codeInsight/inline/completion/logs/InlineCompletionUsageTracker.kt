// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.logs

import com.intellij.codeInsight.inline.completion.InlineCompletionEventAdapter
import com.intellij.codeInsight.inline.completion.InlineCompletionEventType
import com.intellij.codeInsight.inline.completion.InlineCompletionRequest
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventFields.createAdditionalDataField
import com.intellij.internal.statistic.eventLog.events.ObjectEventField
import com.intellij.internal.statistic.eventLog.events.VarargEventId
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.util.application
import com.intellij.util.containers.ContainerUtil
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.coroutines.cancellation.CancellationException

object InlineCompletionUsageTracker : CounterUsagesCollector() {
  private val GROUP = EventLogGroup("inline.completion", 20)

  const val INVOKED_EVENT_ID = "invoked"
  internal object InvokedEvents {
    val REQUEST_ID = EventFields.Long("request_id")
    val EVENT = EventFields.Class("event")
    val PROVIDER = EventFields.Class("provider")
    val TIME_TO_COMPUTE = EventFields.Long("time_to_compute")
    val OUTCOME = EventFields.NullableEnum<Outcome>("outcome")

    enum class Outcome {
      EXCEPTION,
      CANCELED,
      SHOW,
      NO_SUGGESTIONS
    }

    val ADDITIONAL: ObjectEventField = createAdditionalDataField(GROUP.id, INVOKED_EVENT_ID)
    val CONTEXT_FEATURES = ObjectEventField(
      "context_features",
      InlineContextFeatures.LINE_NUMBER,
      InlineContextFeatures.COLUMN_NUMBER,
      InlineContextFeatures.SYMBOLS_IN_LINE_BEFORE_CARET,
      InlineContextFeatures.SYMBOLS_IN_LINE_AFTER_CARET,
      InlineContextFeatures.IS_WHITE_SPACE_BEFORE_CARET,
      InlineContextFeatures.IS_WHITE_SPACE_AFTER_CARET,
      InlineContextFeatures.NON_SPACE_SYMBOL_BEFORE_CARET,
      InlineContextFeatures.NON_SPACE_SYMBOL_AFTER_CARET,
      InlineContextFeatures.PREVIOUS_EMPTY_LINES_COUNT,
      InlineContextFeatures.PREVIOUS_NON_EMPTY_LINE_LENGTH,
      InlineContextFeatures.FOLLOWING_EMPTY_LINES_COUNT,
      InlineContextFeatures.FOLLOWING_NON_EMPTY_LINE_LENGTH,
      InlineContextFeatures.FIRST_PARENT,
      InlineContextFeatures.SECOND_PARENT,
      InlineContextFeatures.TIME_SINCE_LAST_TYPING,
      *TypingSpeedTracker.getEventFields(),
    )
    val CONTEXT_FEATURES_COMPUTATION_TIME = EventFields.Long("context_features_computation_time")
  }

  internal val INVOKED_EVENT: VarargEventId = GROUP.registerVarargEvent(
    INVOKED_EVENT_ID,
    InvokedEvents.REQUEST_ID,
    EventFields.Language,
    EventFields.CurrentFile,
    InvokedEvents.EVENT,
    InvokedEvents.PROVIDER,
    InvokedEvents.TIME_TO_COMPUTE,
    InvokedEvents.OUTCOME,
    InvokedEvents.ADDITIONAL,
    InvokedEvents.CONTEXT_FEATURES,
    InvokedEvents.CONTEXT_FEATURES_COMPUTATION_TIME,
  )

  object ShownEvents {
    val REQUEST_ID = EventFields.Long("request_id")
    val PROVIDER = EventFields.Class("provider")
    val LINES = EventFields.Int("lines")
    val LENGTH = EventFields.Int("length")
    val TYPING_DURING_SHOW = EventFields.Int("typing_during_show")

    val TIME_TO_SHOW = EventFields.Long("time_to_show")
    val SHOWING_TIME = EventFields.Long("showing_time")
    val FINISH_TYPE = EventFields.Enum<FinishType>("finish_type")

    enum class FinishType {
      SELECTED,
      TYPED,
      ESCAPE_PRESSED,
      BACKSPACE_PRESSED,
      KEY_PRESSED,
      INVALIDATED,
      MOUSE_PRESSED,
      CARET_CHANGED,
      DOCUMENT_CHANGED,
      EDITOR_REMOVED,
      FOCUS_LOST,
      EMPTY,
      ERROR,
      OTHER
    }
  }

  internal val SHOWN_EVENT: VarargEventId = GROUP.registerVarargEvent(
    "shown",
    ShownEvents.REQUEST_ID,
    EventFields.Language,
    EventFields.CurrentFile,
    ShownEvents.PROVIDER,
    ShownEvents.LINES,
    ShownEvents.LENGTH,
    ShownEvents.TYPING_DURING_SHOW,
    ShownEvents.TIME_TO_SHOW,
    ShownEvents.SHOWING_TIME,
    ShownEvents.FINISH_TYPE,
  )

  override fun getGroup() = GROUP

  private val requestIds = ContainerUtil.createConcurrentWeakMap<InlineCompletionRequest, Long>()

  fun getRequestId(request: InlineCompletionRequest): Long = requestIds[request] ?: -1

  class Listener : InlineCompletionEventAdapter {
    private val lock = ReentrantLock()
    private var invocationTracker: InlineCompletionInvocationTracker? = null
    private var showTracker: InlineCompletionShowTracker? = null

    override fun onRequest(event: InlineCompletionEventType.Request) = lock.withLock {
      invocationTracker = InlineCompletionInvocationTracker(event).also {
        requestIds[event.request] = it.requestId
        application.runReadAction { it.captureContext(event.request.editor, event.request.endOffset) }
      }
    }

    override fun onShow(event: InlineCompletionEventType.Show) = lock.withLock {
      if (event.i == 0 && !event.element.text.isEmpty()) {
        invocationTracker?.hasSuggestion()
      }
      if (event.i == 0) {
        // invocation tracker -> show tracker
        showTracker = invocationTracker?.createShowTracker()
        showTracker!!.firstShown(event.element)
      }
      if (event.i != 0) {
        showTracker!!.nextShown(event.element)
      }
    }

    override fun onChange(event: InlineCompletionEventType.Change) {
      showTracker!!.truncateTyping(event.overtypedLength)
    }

    override fun onInsert(event: InlineCompletionEventType.Insert): Unit = lock.withLock {
      showTracker?.selected()
    }

    override fun onHide(event: InlineCompletionEventType.Hide): Unit = lock.withLock {
      showTracker?.canceled(event.finishType)
    }

    override fun onEmpty(event: InlineCompletionEventType.Empty): Unit = lock.withLock {
      invocationTracker?.noSuggestions()
    }

    override fun onCompletion(event: InlineCompletionEventType.Completion): Unit = lock.withLock {
      if (!event.isActive || event.cause is CancellationException || event.cause is ProcessCanceledException) {
        invocationTracker?.canceled()
      }
      else if (event.cause != null) {
        invocationTracker?.exception()
      }
      invocationTracker?.finished()
      invocationTracker = null
    }
  }
}
