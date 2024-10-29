// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.logs

import com.intellij.codeInsight.inline.completion.InlineCompletionEventType
import com.intellij.codeInsight.inline.completion.logs.InlineCompletionLogsUtils.isLoggable
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.eventLog.events.*
import com.intellij.internal.statistic.eventLog.events.EventFields.createAdditionalDataField
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.internal.statistic.utils.PluginInfo
import com.intellij.util.application
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.coroutines.cancellation.CancellationException

@ApiStatus.Internal
object InlineCompletionUsageTracker : CounterUsagesCollector() {
  private val GROUP = EventLogGroup("inline.completion", 36)

  const val INVOKED_EVENT_ID: String = "invoked"
  const val SHOWN_EVENT_ID: String = "shown"
  const val INSERTED_STATE_EVENT_ID: String = "inserted_state"

  private class PluginInfoField(override val name: String, override val description: String?) : PrimitiveEventField<PluginInfo?>() {
    override val validationRule: List<String>
      get() = listOf("plugin_info")

    override fun addData(
      fuData: FeatureUsageData,
      value: PluginInfo?,
    ) {
      if (value == null || !value.type.isSafeToReport()) return
      val id = value.id
      if (!id.isNullOrEmpty()) {
        fuData.addData(name, id)
      }
    }
  }

  @ApiStatus.Internal
  object InvokedEvents {
    val REQUEST_ID: EventField<Long> = EventFields.Long("request_id", "ID of the request. Use it to match invoked, shown and inserted_state events")
    val EVENT: EventField<Class<*>?> = EventFields.Class("event", "Event which triggered completion")
    val PROVIDER: EventField<Class<*>?> = EventFields.Class("provider", "Completion provider class")
    val PROVIDER_PLUGIN_INFO: EventField<PluginInfo?> = PluginInfoField("plugin_id_of_provider", "Id of provider's plugin")
    val TIME_TO_COMPUTE: EventField<Long> = EventFields.Long("time_to_compute", "Time of provider execution (ms)")
    val OUTCOME: EventField<Outcome?> = EventFields.NullableEnum<Outcome>("outcome", description = "Invocation outcome (show, no_suggestions, etc.)")

    enum class Outcome {
      EXCEPTION,
      CANCELED,
      SHOW, // Only if a suggestion is computed entirely
      NO_SUGGESTIONS
    }

    val ADDITIONAL: ObjectEventField = createAdditionalDataField(GROUP.id, INVOKED_EVENT_ID)
  }

  internal val INVOKED_EVENT: VarargEventId = GROUP.registerVarargEvent(
    INVOKED_EVENT_ID,
    InvokedEvents.REQUEST_ID,
    EventFields.Language,
    EventFields.CurrentFile,
    InvokedEvents.EVENT,
    InvokedEvents.PROVIDER,
    InvokedEvents.PROVIDER_PLUGIN_INFO,
    InvokedEvents.TIME_TO_COMPUTE,
    InvokedEvents.OUTCOME,
    InvokedEvents.ADDITIONAL,
  )

  @ApiStatus.Internal
  object ShownEvents {
    val REQUEST_ID: EventField<Long> = EventFields.Long("request_id", "ID of the request. Use it to match invoked, shown and inserted_state events")
    val PROVIDER: EventField<Class<*>?> = EventFields.Class("provider", "Completion provider class")
    val LINES: EventField<List<Int>> = EventFields.IntList("lines", "Number of lines in the suggestion")
    val LENGTH: EventField<List<Int>> = EventFields.IntList("length", "Length of the 'gray text' shown (in chars)")
    val LENGTH_CHANGE_DURING_SHOW: EventField<Int> = EventFields.Int("typing_during_show", "How many chars the user typed over completion or length of partially accepted completion")

    val TIME_TO_SHOW: EventField<Long> = EventFields.Long("time_to_show", "Time between completion invocation time and show time (ms)")
    val SHOWING_TIME: EventField<Long> = EventFields.Long("showing_time", "Period of time for which the user was looking at the suggestion (ms)")
    val FINISH_TYPE: EventField<FinishType> = EventFields.Enum<FinishType>("finish_type", "How completion session was finished")

    val EXPLICIT_SWITCHING_VARIANTS_TIMES: EventField<Int> = EventFields.Int("explicit_switching_variants_times", "How many times the user was switching between completion variants (we only have 1 at the moment)")
    val SELECTED_INDEX: EventField<Int> = EventFields.Int("selected_index")

    @Serializable
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
    SHOWN_EVENT_ID,
    ShownEvents.REQUEST_ID,
    EventFields.Language,
    EventFields.CurrentFile,
    ShownEvents.PROVIDER,
    ShownEvents.LINES,
    ShownEvents.LENGTH,
    ShownEvents.LENGTH_CHANGE_DURING_SHOW,
    ShownEvents.TIME_TO_SHOW,
    ShownEvents.SHOWING_TIME,
    ShownEvents.FINISH_TYPE,
    ShownEvents.EXPLICIT_SWITCHING_VARIANTS_TIMES,
    ShownEvents.SELECTED_INDEX
  )

  @ApiStatus.Internal
  object InsertedStateEvents {
    val SUGGESTION_LENGTH: EventField<Int> = EventFields.Int("suggestion_length", "Length of the suggestion")
    val RESULT_LENGTH: EventField<Int> = EventFields.Int("result_length", "Length of what remained in Editor")
    val EDIT_DISTANCE: EventField<Int> = EventFields.Int("edit_distance", "Edit distance between the suggestion and what remained in Editor")
    val EDIT_DISTANCE_NO_ADD: EventField<Int> = EventFields.Int("edit_distance_no_add", "Edit distance the suggestion and what remained in Editor, no counting additions")
    val COMMON_PREFIX_LENGTH: EventField<Int> = EventFields.Int("common_prefix_length", "Length of common prefix between the suggestion and what remained in Editor")
    val COMMON_SUFFIX_LENGTH: EventField<Int> = EventFields.Int("common_suffix_length", "Length of common suffix between the suggestion and what remained in Editor")
  }

  internal val INSERTED_STATE_EVENT: VarargEventId = GROUP.registerVarargEvent(
    eventId = INSERTED_STATE_EVENT_ID,
    description = "State of the inserted inline proposal in the editor after some time",
    ShownEvents.REQUEST_ID,
    EventFields.Language,
    EventFields.CurrentFile,
    EventFields.DurationMs,
    InsertedStateEvents.SUGGESTION_LENGTH,
    InsertedStateEvents.RESULT_LENGTH,
    InsertedStateEvents.EDIT_DISTANCE,
    InsertedStateEvents.EDIT_DISTANCE_NO_ADD,
    InsertedStateEvents.COMMON_PREFIX_LENGTH,
    InsertedStateEvents.COMMON_SUFFIX_LENGTH,
  )

  override fun getGroup(): EventLogGroup = GROUP

  internal class Listener : InlineCompletionFilteringEventListener() {
    private val lock = ReentrantLock()
    private var invocationTracker: InlineCompletionInvocationTracker? = null
    private var showTracker: InlineCompletionShowTracker? = null

    override fun isApplicable(requestEvent: InlineCompletionEventType.Request): Boolean {
      return requestEvent.provider.isLoggable()
    }

    override fun onRequest(event: InlineCompletionEventType.Request): Unit = lock.withLock {
      invocationTracker = InlineCompletionInvocationTracker(event).also {
        application.runReadAction { it.captureContext(event.request.editor, event.request.endOffset) }
      }
      showTracker = null // Just in case
    }

    override fun onComputed(event: InlineCompletionEventType.Computed): Unit = lock.withLock {
      if (showTracker == null) {
        showTracker = invocationTracker!!.createShowTracker()
      }
      if (!event.element.text.isEmpty()) {
        invocationTracker?.hasSuggestions()
      }
      if (event.i == 0) {
        showTracker!!.firstComputed(event.variantIndex, event.element)
      }
      if (event.i != 0) {
        showTracker!!.nextComputed(event.variantIndex, event.element)
      }
    }

    override fun onChange(event: InlineCompletionEventType.Change) {
      val newText = event.elements.joinToString("") { it.text }
      showTracker!!.suggestionChanged(event.variantIndex, event.lengthChange, newText)
    }

    override fun onInsert(event: InlineCompletionEventType.Insert): Unit = lock.withLock {
      showTracker?.selected()
    }

    override fun onAfterInsert(event: InlineCompletionEventType.AfterInsert) {
      showTracker?.inserted()
      showTracker = null
    }

    override fun onHide(event: InlineCompletionEventType.Hide): Unit = lock.withLock {
      showTracker?.canceled(event.finishType)
      // If the suggestion is selected, 'showTracker' will be used in 'onAfterInsert' and nullified there
      if (event.finishType != ShownEvents.FinishType.SELECTED) {
        showTracker = null
      }
    }

    override fun onVariantSwitched(event: InlineCompletionEventType.VariantSwitched) {
      showTracker?.variantSwitched(event.toVariantIndex, event.explicit)
    }

    override fun onNoVariants(event: InlineCompletionEventType.NoVariants) {
      invocationTracker?.noSuggestions()
    }

    override fun onCompletion(event: InlineCompletionEventType.Completion): Unit = lock.withLock {
      if (!event.isActive || event.cause is CancellationException) {
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
