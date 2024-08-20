// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.logs

import com.intellij.concurrency.ConcurrentCollectionFactory
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.eventLog.events.ObjectEventData
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.removeUserData
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ConcurrentLinkedQueue

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

  private val asyncAdds = ConcurrentLinkedQueue<Job>()

  private suspend fun awaitAllAlreadyRunningAsyncAdds() {
    while (currentCoroutineContext().isActive) {
      val job = asyncAdds.poll() ?: return
      job.join()
    }
  }

  private fun cancelAsyncAdds() {
    while (true) {
      val job = asyncAdds.poll() ?: break
      job.cancel()
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
    val job = InlineCompletionLogsScopeProvider.getInstance().cs.launch {
      block().forEach { add(it) }
    }
    asyncAdds.add(job)
  }

  /**
   * Cancel all [asyncAdds] and send current log container.
   * Await for this function completion before exit from the inline completion request and process next typings or next requests.
   * Should be very fast.
   */
  fun logCurrent() {
    cancelAsyncAdds()
    InlineCompletionLogs.Session.SESSION_EVENT.log( // log function is asynchronous, so it's ok to launch it even on EDT
      logs.filter { it.value.isNotEmpty() }.map { (phase, events) ->
        InlineCompletionLogs.Session.phases[phase]!!.with(ObjectEventData(events.toList()))
      }
    )
    logs.forEach { (_, events) -> events.clear() }
  }

  /**
   * Wait for all [asyncAdds] and return current logs to use as features for some model.
   */
  suspend fun getCollectedLogs(): List<EventPair<*>> {
    awaitAllAlreadyRunningAsyncAdds()
    return logs.values.flatten()
  }

  companion object {
    private val KEY: Key<InlineCompletionLogsContainer> = Key("inline.completion.logs.container")

    /**
     * Create, store in editor and get log container
     */
    fun create(editor: Editor): InlineCompletionLogsContainer {
      val container = InlineCompletionLogsContainer()
      editor.putUserData(KEY, container)
      return container
    }

    fun get(editor: Editor): InlineCompletionLogsContainer? {
      return editor.getUserData(KEY)
    }

    /**
     * Remove container from editor and return it. This function intentionally does not cancel tasks added from [addAsync].
     * You still can await for logs to be collected and log all of them.
     */
    fun remove(editor: Editor): InlineCompletionLogsContainer? {
      return editor.removeUserData(KEY)
    }
  }
}