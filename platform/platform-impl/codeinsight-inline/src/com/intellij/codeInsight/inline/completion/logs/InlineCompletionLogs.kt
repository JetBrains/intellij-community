// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.logs

import com.intellij.codeInsight.inline.completion.logs.InlineCompletionLogsContainer.Phase
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.StatisticsEventLoggerProvider
import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventId2
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.eventLog.events.ObjectEventField
import com.intellij.internal.statistic.eventLog.events.VarargEventId
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.progress.Cancellation
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object InlineCompletionLogs : CounterUsagesCollector() {
  // TODO use ML_RECORDER_ID
  val GROUP: EventLogGroup = EventLogGroup("inline.completion.v2", 65, recorder = "ML")

  val INSERTED_STATE_EVENT: VarargEventId = GROUP.registerVarargEvent(
    "inserted_state",
    InsertedStateEvents.REQUEST_ID,
    EventFields.DurationMs,
    InsertedStateEvents.SUGGESTION_LENGTH,
    InsertedStateEvents.RESULT_LENGTH,
    InsertedStateEvents.EDIT_DISTANCE,
    InsertedStateEvents.EDIT_DISTANCE_NO_ADD,
    InsertedStateEvents.COMMON_PREFIX_LENGTH,
    InsertedStateEvents.COMMON_SUFFIX_LENGTH,
    EventFields.Language,
  )

  val modelRequestSent: EventId2<Long, Class<*>?> = GROUP.registerEvent(
    "model_request_sent",
    EventFields.Long("request_id"),
    EventFields.Class("client"),
  )

  override fun getGroup(): EventLogGroup = GROUP

  fun onModelRequestSent(project: Project, requestId: Long, clientClass: Class<*>) {
    modelRequestSent.log(project, requestId, clientClass)
  }

  init {
    // Force Session to load so the "session" FUS event is registered at collector load. Goes through the
    // subscription-free `preload` (not an accessor) so class initialization does not request a service.
    Session.preload()
  }

  private val EP_NAME = ExtensionPointName.create<StatisticsEventLoggerProvider>("com.intellij.statistic.eventLog.eventLoggerProvider")

  private val mlRecorder = lazy {
    EP_NAME.extensionList.firstOrNull { it.recorderId == "ML" }
  }

  // most essential logs
  private val essentialLogs = listOf("experiment_group", "file_language", "finish_type", "request_id", "was_shown", "inline_api_provider", "total_inserted_length")

  // these options are provided by AP FUS config
  val basicFields: Collection<String>
    get() = mlRecorder.value?.recorderOptionsProvider?.getListOption("basic_logs")?.takeIf { it.isNotEmpty() } ?: essentialLogs
  val fullLogShare: Int
    get() = mlRecorder.value?.recorderOptionsProvider?.getIntOption("local_logs_share") ?: 1
  val cloudLogsShare: Int
    get() = mlRecorder.value?.recorderOptionsProvider?.getIntOption("cloud_logs_share") ?: 10

  object Session {
    /**
     * Immutable snapshot of the EP-derived state. All fields are mutually consistent (in particular
     * [sessionEvent] is registered with exactly the [phases] instances, which FUS validates by identity).
     * Swapped atomically as a whole via the single volatile [snapshot] reference, so a reader that reads
     * [snapshot] once always observes a consistent set - no per-field tearing between `phases`/`sessionEvent`.
     */
    class Snapshot internal constructor(
      val phases: Map<Phase, ObjectEventField>,
      val phaseByName: Map<String, Phase>,
      val sessionEvent: VarargEventId,
    )

    @Volatile
    private var snapshot: Snapshot = build()

    // group logs to the phase so that each phase has its own object field
    val phases: Map<Phase, ObjectEventField> get() = current().phases

    val phaseByName: Map<String, Phase> get() = current().phaseByName

    // Each phase has a separate ObjectEventField in the session event with the corresponding features.
    val SESSION_EVENT: VarargEventId get() = current().sessionEvent

    // Log fields are contributed via the dynamic InlineCompletionSessionLogsEP. The EP-derived state is
    // snapshotted at class load, but extensions may be added/removed later - dynamic plugins at runtime, and,
    // notably, tests that register their own log groups in setUp after this object is already initialized.
    // Rebuild the snapshot on every EP change, subscribing lazily on first snapshot read (see `current`). The
    // subscription needs a service (a coroutine scope), which the service container forbids requesting from an
    // init; reads happen at runtime, so this stays out of class initialization.
    private val subscription: Lazy<Unit> = lazy {
      val cs = InlineCompletionLogsScopeProvider.getInstance().cs
      // Rebuild once: extensions may have been added before we subscribed (tests register EPs in setUp,
      // dynamic plugins may load before the first inline-completion session).
      snapshot = build()
      InlineCompletionSessionLogsEP.EP_NAME.point.addChangeListener(cs) {
        snapshot = build()
      }
    }

    /**
     * Returns the current snapshot, subscribing to EP changes on first access so any reader (not just the log
     * container) keeps an up-to-date snapshot without an explicit setup call. Reached only from the accessors,
     * i.e. at runtime - never from init (which uses the subscription-free [preload]).
     */
    private fun current(): Snapshot {
      subscription.value
      return snapshot
    }

    /**
     * Forces this object to initialize (building the initial snapshot and registering the "session" FUS event)
     * without touching the subscription, so it is safe to call from [InlineCompletionLogs]'s class initializer.
     * The call itself triggers `<clinit>`, which runs the `snapshot` field initializer - nothing else is needed.
     */
    internal fun preload() = Unit

    fun isBasic(eventPair: EventPair<*>): Boolean {
      return basicFields.contains(eventPair.field.name)
    }

    private fun build(): Snapshot {
      val phaseToFieldList = computePhaseToFieldList()
      val phases = Phase.entries.associateWith { phase ->
        ObjectEventField(phase.name.lowercase(), phase.description, *phaseToFieldList.filter { phase == it.first }.map { it.second }.toTypedArray())
      }
      val phaseByName = phaseToFieldList.associate { it.second.name to it.first }
      // FUS validates logged fields by instance identity, so the session event must reference these exact
      // ObjectEventField instances - register it together with `phases` inside the same snapshot.
      val sessionEvent = GROUP.registerVarargEvent("session", *phases.values.toTypedArray())
      return Snapshot(phases, phaseByName, sessionEvent)
    }

    private fun computePhaseToFieldList(): List<Pair<Phase, EventField<*>>> {
      val fields = Cancellation.withNonCancelableSection().use {
        // Non-cancellable section, because this function is often used in
        // static initializer code of `object`, and any exception (namely, CancellationException)
        // breaks the object with ExceptionInInitializerError, and subsequent NoClassDefFoundError
        InlineCompletionSessionLogsEP.EP_NAME.extensionsIfPointIsRegistered
      }.flatMap { it.logGroups }.flatMap { phasedLogs ->
        phasedLogs.registeredFields.map { field -> phasedLogs.phase to field }
      }

      fields.groupingBy { it.second }.eachCount().filter { it.value > 1 }.forEach {
        thisLogger().error("Log ${it.key} is registered multiple times: ${it.value}")
      }
      return fields
    }
  }

  @ApiStatus.Internal
  object InsertedStateEvents {
    val REQUEST_ID: EventField<Long> = EventFields.Long("request_id", "ID of the request. Use it to match invoked, shown and inserted_state events")
    val SUGGESTION_LENGTH: EventField<Int> = EventFields.Int("suggestion_length", "Length of the suggestion")
    val RESULT_LENGTH: EventField<Int> = EventFields.Int("result_length", "Length of what remained in Editor")
    val EDIT_DISTANCE: EventField<Int> = EventFields.Int("edit_distance", "Edit distance between the suggestion and what remained in Editor")
    val EDIT_DISTANCE_NO_ADD: EventField<Int> = EventFields.Int("edit_distance_no_add", "Edit distance the suggestion and what remained in Editor, no counting additions")
    val COMMON_PREFIX_LENGTH: EventField<Int> = EventFields.Int("common_prefix_length", "Length of common prefix between the suggestion and what remained in Editor")
    val COMMON_SUFFIX_LENGTH: EventField<Int> = EventFields.Int("common_suffix_length", "Length of common suffix between the suggestion and what remained in Editor")
  }
}
