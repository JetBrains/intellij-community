// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.logs

import com.intellij.codeInsight.inline.completion.logs.InlineCompletionLogsContainer.Phase
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.StatisticsEventLoggerProvider
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.eventLog.events.ObjectEventField
import com.intellij.internal.statistic.eventLog.events.VarargEventId
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.progress.Cancellation
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object InlineCompletionLogs : CounterUsagesCollector() {
  // TODO use ML_RECORDER_ID
  val GROUP = EventLogGroup("inline.completion.v2", 28, recorder = "ML")

  override fun getGroup(): EventLogGroup = GROUP

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
    private val phaseToFieldList: List<Pair<Phase, EventFieldExt<*>>> = run {
      val fields = Cancellation.withNonCancelableSection().use {
        // Non-cancellable section, because this function is often used in
        // static initializer code of `object`, and any exception (namely, CancellationException)
        // breaks the object with ExceptionInInitializerError, and subsequent NoClassDefFoundError
        InlineCompletionSessionLogsEP.EP_NAME.extensionsIfPointIsRegistered
      }.flatMap { it.logGroups }.flatMap { phasedLogs ->
        phasedLogs.registeredFields.map { field -> phasedLogs.phase to field}
      }

      fields.groupingBy { it.second.field }.eachCount().filter { it.value > 1 }.forEach {
        thisLogger().error("Log ${it.key} is registered multiple times: ${it.value}")
      }
      fields
    }

    // group logs to the phase so that each phase has its own object field
    val phases: Map<Phase, ObjectEventField> = Phase.entries.associateWith { phase ->
      ObjectEventField(phase.name.lowercase(), phase.description, *phaseToFieldList.filter { phase == it.first }.map { it.second.field }.toTypedArray())
    }

    val eventFieldProperties: Map<String, EventFieldProperty> = phaseToFieldList.associate {
      it.second.field.name to EventFieldProperty(it.first, it.second.isBasic)
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

  data class EventFieldProperty(
    val phase: Phase,
    val isBasic: Boolean,
  )
}
