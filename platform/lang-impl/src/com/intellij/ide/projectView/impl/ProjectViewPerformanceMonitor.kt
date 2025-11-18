// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectView.impl

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
import com.intellij.ui.tree.TreeModelUpdateRequest
import com.intellij.ui.treeStructure.ProjectViewUpdateCause
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.swing.tree.TreePath
import kotlin.concurrent.atomics.*
import kotlin.time.ComparableTimeMark
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeSource

@OptIn(ExperimentalAtomicApi::class)
@Service(Level.PROJECT)
internal class ProjectViewPerformanceMonitor(
  coroutineScope: CoroutineScope,
) {
  companion object {
    @JvmStatic fun getInstance(project: Project): ProjectViewPerformanceMonitor = project.service()
  }
  
  private val requestId = AtomicLong(0L)
  private val events = Channel<Event>(capacity = Channel.UNLIMITED)

  init {
    coroutineScope.launch(CoroutineName("ProjectViewPerformanceMonitor heartbeat")) {
      while (true) {
        delay(1.minutes)
        events.send(HeartbeatEvent)
      }
    }
    coroutineScope.launch(context = CoroutineName("ProjectViewPerformanceMonitor events")) {
      val stats = Stats()
      try {
        for (event in events) {
          stats.processEvent(event)
        }
      }
      finally {
        events.close() // to prevent the channel from growing infinitely in the case something crashes here
      }
    }
  }

  fun beginUpdateAll(causes: Collection<ProjectViewUpdateCause>): TreeModelUpdateRequest {
    return Request("The entire PV", requestId.incrementAndFetch(), causes)
  }

  fun beginUpdatePath(path: TreePath, structure: Boolean, causes: Collection<ProjectViewUpdateCause>): TreeModelUpdateRequest {
    return Request("The path=$path, structure=$structure", requestId.incrementAndFetch(), causes)
  }

  private inner class Request(private val what: String, val id: Long, causes: Collection<ProjectViewUpdateCause>) : TreeModelUpdateRequest {
    private val startedTime = TimeSource.Monotonic.markNow()
    private val count = AtomicInt(0)
    private val finished = AtomicBoolean(false)
    
    init {
      LOG.trace { "[request $id] $what is updated because $causes" }
      events.trySend(StartedEvent(id, causes, startedTime))
    }
    
    override fun nodesLoaded(count: Int) {
      this.count.addAndFetch(count)
      events.trySend(LoadedEvent(id, count))
    }

    override fun finished() {
      if (!finished.compareAndSet(false, true)) return
      val finishedTime = TimeSource.Monotonic.markNow()
      val elapsed = finishedTime - startedTime
      val count = count.load()
      LOG.trace { "[request $id] The update has finished in $elapsed, $count nodes updated" }
      events.trySend(FinishedEvent(id, finishedTime, count))
    }
  }

  private sealed class Event

  private data object HeartbeatEvent : Event()

  private sealed interface RequestEvent {
    val id: Long
  }

  private data class StartedEvent(
    override val id: Long,
    val causes: Collection<ProjectViewUpdateCause>,
    val startedTime: ComparableTimeMark,
  ): Event(), RequestEvent

  private data class LoadedEvent(
    override val id: Long,
    val loadedCount: Int,
  ): Event(), RequestEvent

  private data class FinishedEvent(
    override val id: Long,
    val finishedTime: ComparableTimeMark,
    val count: Int,
  ): Event(), RequestEvent

  private class Stats {
    private var current = StatsSample()

    fun processEvent(event: Event) {
      when (event) {
        is StartedEvent -> current.requestStarted(event)
        is LoadedEvent -> current.requestLoadedCountChanged(event)
        is FinishedEvent -> current.requestFinished(event)
        is HeartbeatEvent -> reportSample()
      }
    }

    private fun reportSample() {
      current.report()
      current = current.startNewSample()
    }
  }

  private class StatsSample {
    private var startedRequests = 0
    private var activeRequests = 0
    private var finishedRequests = 0
    private val requestStates = hashMapOf<Long, RequestState>()
    private val causeStates = hashMapOf<ProjectViewUpdateCause, CauseState>()
    private val causeReports = hashMapOf<ProjectViewUpdateCause, CauseReport>()

    fun requestStarted(event: StartedEvent) {
      requestStates[event.id] = RequestState(event)
      ++activeRequests
      ++startedRequests
      for (cause in event.causes) {
        val causeState = causeStates.getOrPut(cause) { CauseState(event.startedTime) }
        ++causeState.activeRequests
        val causeReport = causeReports.getOrPut(cause) { CauseReport() }
        causeReport.activeRequests = causeState.activeRequests
      }
    }

    fun requestLoadedCountChanged(event: LoadedEvent) {
      val requestState = requestStates[event.id]
      if (requestState == null) {
        LOG.warn(Throwable("Got an event for a request that doesn't exist: $event"))
        return
      }
      requestState.loadedNodeCount += event.loadedCount
      for (cause in requestState.causes) {
        val causeState = ensureNotNull(cause, causeStates[cause]) ?: continue
        causeState.loadedNodeCount += event.loadedCount
      }
    }

    fun requestFinished(event: FinishedEvent) {
      val requestState = requestStates.remove(event.id)
      if (requestState == null) {
        LOG.warn(Throwable("Got an event for a request that doesn't exist: $event"))
        return
      }
      --activeRequests
      ++finishedRequests
      requestState.finishedTime = event.finishedTime
      for (cause in requestState.causes) {
        val causeState = ensureNotNull(cause, causeStates[cause]) ?: continue
        --causeState.activeRequests
        val causeReport = ensureNotNull(cause, causeReports[cause]) ?: continue
        causeReport.activeRequests = causeState.activeRequests
        if (causeState.activeRequests == 0) {
          allRequestsForCauseFinished(cause, event)
        }
      }
    }

    private fun allRequestsForCauseFinished(cause: ProjectViewUpdateCause, event: FinishedEvent) {
      val causeState = ensureNotNull(cause, causeStates.remove(cause)) ?: return
      val causeReport = ensureNotNull(cause, causeReports[cause]) ?: return
      ++causeReport.completedRequests
      causeReport.loadedNodeCount += causeState.loadedNodeCount
      causeReport.timeSpent += event.finishedTime - causeState.startedTime
    }

    private fun <T> ensureNotNull(cause: ProjectViewUpdateCause, result: T?): T? {
      if (result != null) return result
      LOG.warn(Throwable(
        "No state registered yet for the update cause $cause, the current state is\n" +
        "$requestStates\n" +
        "$causeStates\n" +
        "$causeReports"
      ))
      return result
    }

    fun report() {
      if (startedRequests == 0 && activeRequests == 0 && finishedRequests == 0) return // don't spam the log when the IDE is idle
      if (LOG.isDebugEnabled) {
        LOG.debug(
          "Project View performance sample: started requests = $startedRequests, finished requests = $finishedRequests, still active requests = $activeRequests. " +
          "Update causes:"
        )
        for (entry in causeReports.entries.sortedBy { it.key }) {
          LOG.debug("${entry.key}: ${entry.value}")
        }
      }
    }

    fun startNewSample(): StatsSample {
      val newSample = StatsSample()
      newSample.activeRequests = activeRequests
      for ((id, requestState) in requestStates) {
        if (requestState.isStillActive) {
          newSample.requestStates[id] = requestState
        }
      }
      newSample.causeStates.putAll(causeStates)
      for ((cause, causeReport) in causeReports) {
        if (causeReport.activeRequests > 0) {
          newSample.causeReports[cause] = causeReport.copy(
            completedRequests = 0, // we report completed requests per minute
          )
        }
      }
      return newSample
    }
  }

  private data class RequestState(
    val id: Long,
    val causes: Collection<ProjectViewUpdateCause>,
    val startTime: ComparableTimeMark,
    var loadedNodeCount: Int = 0,
    var finishedTime: ComparableTimeMark? = null,
  ) {
    constructor(startedEvent: StartedEvent) : this(startedEvent.id, startedEvent.causes, startedEvent.startedTime)

    val isStillActive: Boolean
      get() = finishedTime == null
  }

  private data class CauseState(
    val startedTime: ComparableTimeMark,
    var activeRequests: Int = 0,
    var loadedNodeCount: Int = 0,
  )

  private data class CauseReport(
    var activeRequests: Int = 0,
    var completedRequests: Int = 0,
    var loadedNodeCount: Int = 0,
    var timeSpent: Duration = Duration.ZERO,
  )
}

private val LOG = logger<ProjectViewPerformanceMonitor>()
