// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.logs

import com.intellij.concurrency.ConcurrentCollectionFactory
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.eventLog.events.ObjectEventData
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.removeUserData
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class InlineCompletionLogsContainer {

  /**
   * Describes phase of the Inline completion session.
   * Each phase can have multiple features (logs)
   */
  @ApiStatus.Internal
  enum class Phase(val description: String) {
    INLINE_API_STARTING("Execution inside inline completion API"),
    CONTEXT_COLLECTION("During context collecting"),
    COMPLETION_MODEL_EXECUTION("During model execution"),
    POSTPROCESSING_BEFORE_FILTER_MODEL("During postprocessing, before filter model"),
    PROVIDER_FINISHING("End of postprocessing, end of pipeline"),
    INLINE_API_FINISHING("Finishing execution inside inline completion API"),
    ;
  }

  private val logs: Map<Phase, MutableSet<EventPair<*>>> = Phase.entries.associateWith {
    ConcurrentCollectionFactory.createConcurrentSet<EventPair<*>>()
  }

  private val asyncAdds = Channel<Deferred<*>>(capacity = Channel.UNLIMITED)

  private suspend fun waitForAsyncAdds() {
    while (currentCoroutineContext().isActive) {
      val job = asyncAdds.tryReceive().getOrNull() ?: break
      job.await()
    }
  }

  /**
   * Use to add log to log container.
   * If you have to launch expensive computation and don't want to pause your main execution (especially if you are on EDT) use [addAsync].
   */
  fun add(value: EventPair<*>) {
    val phase = requireNotNull(InlineCompletionLogs.Session.eventFieldNameToPhase[value.field.name]) {
      "Cannot find phase for ${value.field.name}"
    }
    logs[phase]!!.add(value)
  }

  /**
   * Use [add] if there is no special need to use async variant. See [add] documentation for more info.
   */
  fun addAsync(block: suspend () -> List<EventPair<*>>) {
    val deferred = InlineCompletionLogsScopeProvider.getInstance().cs.async {
      block().forEach { add(it) }
    }
    asyncAdds.trySend(deferred).getOrThrow()
  }

  /**
   * Send log container.
   */
  suspend fun log() {
    waitForAsyncAdds()
    InlineCompletionLogs.Session.SESSION_EVENT.log(
      logs.filter { it.value.isNotEmpty() }.map { (phase, events) ->
        InlineCompletionLogs.Session.phases[phase]!!.with(ObjectEventData(events.toList()))
      }
    )
  }

  /**
   * Get current logs to use as a feature for some model.
   */
  suspend fun currentLogs(): List<EventPair<*>> {
    waitForAsyncAdds()
    return logs.values.flatten()
  }

  companion object {
    private val KEY: Key<InlineCompletionLogsContainer> = Key("inline.completion.logs.container")
    fun create(editor: Editor) {
      editor.putUserData(KEY, InlineCompletionLogsContainer())
    }

    fun get(editor: Editor): InlineCompletionLogsContainer? {
      return editor.getUserData(KEY)
    }

    fun remove(editor: Editor): InlineCompletionLogsContainer? {
      return editor.removeUserData(KEY)
    }
  }
}