// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.logs

import com.intellij.concurrency.ConcurrentCollectionFactory
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.eventLog.events.ObjectEventData
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.removeUserData

class InlineCompletionLogsContainer {

  enum class Step(val description: String) {
    CONTEXT_COLLECTION("Context collection step"),
    COMPLETION_MODEL_EXECUTION("Completion model execution step"),
    POSTPROCESSING("Postprocessing step"),
    FINISH("End of execution show, cancellation, etc step"),
  }

  private val logs: Map<Step, MutableSet<EventPair<*>>> = Step.entries.associateWith {
    ConcurrentCollectionFactory.createConcurrentSet<EventPair<*>>()
  }

  fun add(value: EventPair<*>) {
    val stepName = requireNotNull(InlineCompletionLogs.Session.eventFieldNameToStep[value.field.name]) {
      "Cannot find step for ${value.field.name}"
    }
    logs[stepName]!!.add(value)
  }

  fun log() {
    InlineCompletionLogs.Session.SESSION_EVENT.log(
      logs.map { (step, events) ->
        InlineCompletionLogs.Session.stepToStepField[step]!!.with(ObjectEventData(events.toList<EventPair<*>>()))
      }
    )
  }

  companion object {
    private val KEY: Key<InlineCompletionLogsContainer> = Key("inline.completion.logs.container")
    fun create(editor: Editor) {
      editor.putUserData(KEY, InlineCompletionLogsContainer())
    }

    fun get(editor: Editor): InlineCompletionLogsContainer {
      return requireNotNull(editor.getUserData(KEY)) { "Cannot find logs container" }
    }

    fun remove(editor: Editor): InlineCompletionLogsContainer? {
      return editor.removeUserData(KEY)
    }
  }
}