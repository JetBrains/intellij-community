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
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.coroutines.cancellation.CancellationException

@ApiStatus.Internal
object InlineCompletionUsageTracker : CounterUsagesCollector() {
  private val GROUP = EventLogGroup("inline.completion", 24)

  const val INVOKED_EVENT_ID = "invoked"
  const val COMPUTED_EVENT_ID = "computed"

  @ApiStatus.Internal
  object InvokedEvents {
    val REQUEST_ID = EventFields.Long("request_id")
    val EVENT = EventFields.Class("event")
    val PROVIDER = EventFields.Class("provider")
    val TIME_TO_COMPUTE = EventFields.Long("time_to_compute")
    val OUTCOME = EventFields.NullableEnum<Outcome>("outcome")

    enum class Outcome {
      EXCEPTION,
      CANCELED,
      SHOW, // Only if a suggestion is computed entirely
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
      InlineContextFeatures.TIME_SINCE_LAST_TYPING,
      *InlineContextFeatures.PARENT_FEATURES,
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

  // TODO rename everywhere 'show' and make a better naming
  @ApiStatus.Internal
  object ComputedEvents {
    val REQUEST_ID = EventFields.Long("request_id")
    val PROVIDER = EventFields.Class("provider")
    val LINES = EventFields.IntList("lines")
    val LENGTH = EventFields.IntList("length")
    val TYPING_DURING_SHOW = EventFields.Int("typing_during_show") // TODO name

    val TIME_TO_SHOW = EventFields.Long("time_to_show")
    val SHOWING_TIME = EventFields.Long("showing_time")
    val FINISH_TYPE = EventFields.Enum<FinishType>("finish_type")

    val SWITCHING_VARIANTS_TIMES = EventFields.Int("switching_variants_times") // TODO name
    val SELECTED_INDEX = EventFields.Int("selected_index") // TODO

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
    COMPUTED_EVENT_ID,
    ComputedEvents.REQUEST_ID,
    EventFields.Language,
    EventFields.CurrentFile,
    ComputedEvents.PROVIDER,
    ComputedEvents.LINES,
    ComputedEvents.LENGTH,
    ComputedEvents.TYPING_DURING_SHOW,
    ComputedEvents.TIME_TO_SHOW,
    ComputedEvents.SHOWING_TIME,
    ComputedEvents.FINISH_TYPE,
    ComputedEvents.SWITCHING_VARIANTS_TIMES,
    ComputedEvents.SELECTED_INDEX
  )

  override fun getGroup() = GROUP

  private val requestIds = ContainerUtil.createConcurrentWeakMap<InlineCompletionRequest, Long>()

  fun getRequestId(request: InlineCompletionRequest): Long = requestIds[request] ?: -1

  class Listener : InlineCompletionEventAdapter {
    private val lock = ReentrantLock()
    private var invocationTracker: InlineCompletionInvocationTracker? = null
    private var computationTracker: InlineCompletionComputationTracker? = null

    override fun onRequest(event: InlineCompletionEventType.Request) = lock.withLock {
      invocationTracker = InlineCompletionInvocationTracker(event).also {
        requestIds[event.request] = it.requestId
        application.runReadAction { it.captureContext(event.request.editor, event.request.endOffset) }
      }
      computationTracker = null // Just in case
    }

    override fun onComputed(event: InlineCompletionEventType.Computed) = lock.withLock {
      if (computationTracker == null) {
        computationTracker = invocationTracker!!.createComputationTracker()
      }
      if (!event.element.text.isEmpty()) {
        invocationTracker?.hasSuggestions()
      }
      if (event.i == 0) {
        computationTracker!!.firstComputed(event.variantIndex, event.element)
      }
      if (event.i != 0) {
        computationTracker!!.nextComputed(event.variantIndex, event.element)
      }
    }

    override fun onChange(event: InlineCompletionEventType.Change) {
      computationTracker!!.lengthChanged(event.variantIndex, event.lengthChange)
    }

    override fun onInsert(event: InlineCompletionEventType.Insert): Unit = lock.withLock {
      computationTracker?.selected()
    }

    override fun onHide(event: InlineCompletionEventType.Hide): Unit = lock.withLock {
      computationTracker?.canceled(event.finishType)
      computationTracker = null
    }

    override fun onVariantSwitched(event: InlineCompletionEventType.VariantSwitched) {
      computationTracker?.variantSwitched(event.toVariantIndex, event.explicit)
    }

    override fun onNoVariants(event: InlineCompletionEventType.NoVariants) {
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
