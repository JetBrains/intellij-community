// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.logs

import com.intellij.codeInsight.inline.completion.InlineCompletionEventAdapter
import com.intellij.codeInsight.inline.completion.InlineCompletionEventType
import com.intellij.codeInsight.inline.completion.logs.InlineCompletionLogsContainer.Step
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.ObjectEventField
import com.intellij.internal.statistic.eventLog.events.VarargEventId
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.Cancellation
import kotlin.use

object InlineCompletionLogs : CounterUsagesCollector() {
  // TODO use ML_RECORDER_ID
  val GROUP = EventLogGroup("inline.completion.v2", 1, recorder = "ML")

  override fun getGroup(): EventLogGroup = GROUP

  object Session {
    private val stepToFieldList: List<Pair<Step, EventField<*>>> = run {
      val fields = Cancellation.withNonCancelableSection().use {
        // Non-cancellable section, because this function is often used in
        // static initializer code of `object`, and any exception (namely, CancellationException)
        // breaks the object with ExceptionInInitializerError, and subsequent NoClassDefFoundError
        InlineCompletionSessionLogsEP.EP_NAME.extensionsIfPointIsRegistered
      }.flatMap { it.fields }
      fields.groupingBy { it.second.name }.eachCount().filter { it.value > 1 }.forEach {
        thisLogger().error("Log ${it.key} is registered multiple times: ${it.value}")
      }
      fields
    }

    val stepToStepField: Map<Step, ObjectEventField> = Step.entries.associateWith { step ->
      ObjectEventField(step.name, step.description, *stepToFieldList.filter { step == it.first }.map { it.second }.toTypedArray())
    }

    val eventFieldNameToStep: Map<String, Step> = stepToFieldList.associate { it.second.name to it.first }

    private const val SESSION_EVENT_ID = "session"

    val SESSION_EVENT: VarargEventId = GROUP.registerVarargEvent(
      SESSION_EVENT_ID,
      description = "The whole inline completion session",
      *stepToStepField.values.toTypedArray(),
    )
  }

  class Listener : InlineCompletionEventAdapter {
    private var editor: Editor? = null

    override fun onRequest(event: InlineCompletionEventType.Request) {
      InlineCompletionLogsContainer.create(event.request.editor)
      editor = event.request.editor
    }

    override fun onHide(event: InlineCompletionEventType.Hide) {
      val curEditor = editor ?: return
      val container = InlineCompletionLogsContainer.remove(curEditor) ?: return
      container.log() // TODO move from EDT to background?
    }
  }
}
