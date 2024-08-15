// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.logs

import com.intellij.codeInsight.inline.completion.InlineCompletionEventAdapter
import com.intellij.codeInsight.inline.completion.InlineCompletionEventType
import com.intellij.codeInsight.inline.completion.logs.InlineCompletionLogsContainer.Phase
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.ObjectEventField
import com.intellij.internal.statistic.eventLog.events.VarargEventId
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.Cancellation
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
internal object InlineCompletionLogs : CounterUsagesCollector() {
  // TODO use ML_RECORDER_ID
  val GROUP = EventLogGroup("inline.completion.v2", 2, recorder = "ML")

  override fun getGroup(): EventLogGroup = GROUP

  init {
    Session.SESSION_EVENT // accces session_event to load it
  }

  object Session {
    private val phaseToFieldList: List<Pair<Phase, EventField<*>>> = run {
      val fields = Cancellation.withNonCancelableSection().use {
        // Non-cancellable section, because this function is often used in
        // static initializer code of `object`, and any exception (namely, CancellationException)
        // breaks the object with ExceptionInInitializerError, and subsequent NoClassDefFoundError
        InlineCompletionSessionLogsEP.EP_NAME.extensionsIfPointIsRegistered
      }.flatMap { it.fields }.flatMap { phasedLogs ->
        phasedLogs.fields.map { field -> phasedLogs.phase to field}
      }

      fields.groupingBy { it.second.name }.eachCount().filter { it.value > 1 }.forEach {
        thisLogger().error("Log ${it.key} is registered multiple times: ${it.value}")
      }
      fields
    }

    // group logs to the phase so that each phase has its own object field
    val phases: Map<Phase, ObjectEventField> = Phase.entries.associateWith { phase ->
      ObjectEventField(phase.name.lowercase(), phase.description, *phaseToFieldList.filter { phase == it.first }.map { it.second }.toTypedArray())
    }

    val eventFieldNameToPhase: Map<String, Phase> = phaseToFieldList.associate { it.second.name to it.first }

    val SESSION_EVENT: VarargEventId = GROUP.registerVarargEvent(
      "session",
      description = "The whole inline completion session",
      *phases.values.toTypedArray(),
    )
  }

  class Listener(private val editor: Editor) : InlineCompletionEventAdapter {

    override fun onRequest(event: InlineCompletionEventType.Request) {
      InlineCompletionLogsContainer.create(event.request.editor)
      // TODO log request_id
      // todo async log context features
    }

    override fun onShow(event: InlineCompletionEventType.Show) {
      // todo add was shown event to container
    }

    override fun onHide(event: InlineCompletionEventType.Hide) {
      val container = InlineCompletionLogsContainer.remove(editor) ?: return
      InlineCompletionLogsScopeProvider.getInstance().cs.launch {
        container.log()
      }
    }
  }
}
