// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.logs

import com.intellij.codeInsight.inline.completion.logs.InlineCompletionLogsContainer.Phase
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.eventLog.events.ObjectEventField
import com.intellij.internal.statistic.eventLog.events.VarargEventId
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.Cancellation
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object InlineCompletionLogs : CounterUsagesCollector() {
  // TODO use ML_RECORDER_ID
  val GROUP = EventLogGroup("inline.completion.v2", 22, recorder = "ML")

  override fun getGroup(): EventLogGroup = GROUP

  init {
    Session.SESSION_EVENT // access session_event to load it
  }

  object Session {
    private val phaseToFieldList: List<Pair<Phase, InlineLogs<*>>> = run {
      val fields = Cancellation.withNonCancelableSection().use {
        // Non-cancellable section, because this function is often used in
        // static initializer code of `object`, and any exception (namely, CancellationException)
        // breaks the object with ExceptionInInitializerError, and subsequent NoClassDefFoundError
        InlineCompletionSessionLogsEP.EP_NAME.extensionsIfPointIsRegistered
      }.flatMap { it.logGroups }.flatMap { phasedLogs ->
        phasedLogs.fields.map { field -> phasedLogs.phase to field}
      }

      fields.groupingBy { it.second.first }.eachCount().filter { it.value > 1 }.forEach {
        thisLogger().error("Log ${it.key} is registered multiple times: ${it.value}")
      }
      fields
    }

    // group logs to the phase so that each phase has its own object field
    val phases: Map<Phase, ObjectEventField> = Phase.entries.associateWith { phase ->
      ObjectEventField(phase.name.lowercase(), phase.description, *phaseToFieldList.filter { phase == it.first }.map { it.second.first }.toTypedArray())
    }

    val eventFieldNameToPhase: Map<String, Phase> = phaseToFieldList.associate { it.second.first.name to it.first }

    val eventFieldNameToBasic: Map<String, Boolean> = phaseToFieldList.associate { it.second.first.name to it.second.second }

    fun isBasic(eventPair: EventPair<*>): Boolean = eventFieldNameToBasic[eventPair.field.name] == true

    val SESSION_EVENT: VarargEventId = GROUP.registerVarargEvent(
      "session",
      description = "The whole inline completion session",
      *phases.values.toTypedArray(),
    )
  }

}
