// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.logs

import com.intellij.codeInsight.inline.completion.InlineCompletionEapSupport
import com.intellij.codeInsight.inline.completion.statistics.LocalStatistics
import com.intellij.concurrency.ConcurrentCollectionFactory
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.eventLog.events.ObjectEventData
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.removeUserData
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

private val logger by lazy { logger<InlineCompletionLogsContainer>()}

@ApiStatus.Internal
class InlineCompletionLogsContainer() {

  /**
   * used to determine if all features would be sent or only basic
   */
  private var random: Float = Random.nextFloat()

  private val fullLogsShare: AtomicInteger = AtomicInteger(InlineCompletionLogs.fullLogShare)

  private val forceFullLogs: AtomicBoolean = AtomicBoolean(false)

  private var project: Project? = null

  fun forceFullLogs() {
    forceFullLogs.set(true)
  }

  fun setCloudLogsShare() {
    fullLogsShare.set(InlineCompletionLogs.cloudLogsShare)
  }

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

  @VisibleForTesting
  fun mockRandom(mocked: Float) {
    random = mocked
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

  fun addProject(project: Project?) {
    this.project = project
  }

  /**
   * Use to add log to log container.
   * If you have to launch expensive computation and don't want to pause your main execution (especially if you are on EDT) use [addAsync].
   */
  fun add(value: EventPair<*>) {
    val phase = requireNotNull(InlineCompletionLogs.Session.phaseByName[value.field.name]) {
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
  fun logCurrent(extraLogger: CustomRequestIdLogger? = null) {
    cancelAsyncAdds()

    val filteredEvents = logs.filter { it.value.isNotEmpty() }.mapValues { (_, logs) ->
      // for release, log only basic fields for most of the requests and very rarely log everything.
      if (getShouldSendFullLogs()) {
        logs
      } else {
        logs.filter { pair -> InlineCompletionLogs.Session.isBasic(pair) }
      }
    }

    // log function is asynchronous, so it's ok to launch it even on EDT
    InlineCompletionLogs.Session.SESSION_EVENT.log(project = project, filteredEvents.mapNotNull { (phase, events) ->
      val logPhaseObject = InlineCompletionLogs.Session.phases[phase]
      if (logPhaseObject != null) {
        logPhaseObject.with(ObjectEventData(events.toList()))
      } else {
        logger.error("ObjectEventField is not found for $phase, FUS event may be configured incorrectly!")
        null
      }
    })
    extraLogger?.log(project, filteredEvents)
    logs.map { it.value }.flatten().forEach { LocalStatistics.getInstance().saveIfRegistered(it) }
    logs.forEach { (_, events) -> events.clear() }
  }

  private fun getShouldSendFullLogs(): Boolean {
    return (forceFullLogs.get() // random pass from a filter model
            || InlineCompletionEapSupport.getInstance().isEap() // collect all logs from eap users
            || ApplicationManager.getApplication().isUnitTestMode
            || random < (1f / 100f * fullLogsShare.get())) // occasionally send all logs in release
  }

  /**
   * Wait for all running [asyncAdds] and return current logs to use as features for some model.
   */
  suspend fun awaitAndGetCurrentLogs(): List<EventPair<*>> {
    awaitAllAlreadyRunningAsyncAdds()
    return logs.values.flatten()
  }

  suspend fun awaitAndGetCurrentLogsPhased(): Map<Phase, List<EventPair<*>>> {
    awaitAllAlreadyRunningAsyncAdds()
    return logs.mapValues { it.value.toList() }
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