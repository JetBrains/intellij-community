// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.logs

import com.intellij.codeInsight.inline.completion.logs.InlineCompletionLogsContainer.Phase
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.StatisticsEventLoggerProvider
import com.intellij.internal.statistic.eventLog.events.*
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.progress.Cancellation
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object InlineCompletionLogs : CounterUsagesCollector() {
  // TODO use ML_RECORDER_ID
  val GROUP: EventLogGroup = EventLogGroup("inline.completion.v2", 51, recorder = "ML")

  val INSERTED_STATE_EVENT: VarargEventId = GROUP.registerVarargEvent(
    "inserted_state",
    description = "Tracks the state of the accepted suggestion after some time",
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

  val modelRequestSent: EventId2<Long, Class<*>?> = GROUP.registerEvent("model_request_sent",
                                                                        EventFields.Long("request_id"),
                                                                        EventFields.Class("client"),
                                                                        "Indicates that the call to the model was initiated"
  )

  override fun getGroup(): EventLogGroup = GROUP

  fun onModelRequestSent(project: Project, requestId: Long, clientClass: Class<*>) {
    modelRequestSent.log(project, requestId, clientClass)
  }

  init {
    Session.SESSION_EVENT // access session_event to load it
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
    private val phaseToFieldList: List<Pair<Phase, EventField<*>>> = run {
      val fields = Cancellation.withNonCancelableSection().use {
        // Non-cancellable section, because this function is often used in
        // static initializer code of `object`, and any exception (namely, CancellationException)
        // breaks the object with ExceptionInInitializerError, and subsequent NoClassDefFoundError
        InlineCompletionSessionLogsEP.EP_NAME.extensionsIfPointIsRegistered
      }.flatMap { it.logGroups }.flatMap { phasedLogs ->
        phasedLogs.registeredFields.map { field -> phasedLogs.phase to field}
      }

      fields.groupingBy { it.second }.eachCount().filter { it.value > 1 }.forEach {
        thisLogger().error("Log ${it.key} is registered multiple times: ${it.value}")
      }
      fields
    }

    // group logs to the phase so that each phase has its own object field
    val phases: Map<Phase, ObjectEventField> = Phase.entries.associateWith { phase ->
      ObjectEventField(phase.name.lowercase(), phase.description, *phaseToFieldList.filter { phase == it.first }.map { it.second }.toTypedArray())
    }

    val phaseByName: Map<String, Phase> = phaseToFieldList.associate {
      it.second.name to it.first
    }

    fun isBasic(eventPair: EventPair<*>): Boolean {
      return basicFields.contains(eventPair.field.name)
    }

    // Each phase will have a separate ObjectEventField in the session event with the corresponding features.
    val SESSION_EVENT: VarargEventId = GROUP.registerVarargEvent(
      "session",
      description = "The whole inline completion session",
      *phases.values.toTypedArray(),
    )
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