// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectView.impl

import com.google.common.collect.Comparators.min
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
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import javax.swing.tree.TreePath
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch
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
  private val calculator = ProjectViewUpdatePerformanceCalculator { reportToFus(it) }

  init {
    coroutineScope.launch(CoroutineName("ProjectViewPerformanceMonitor heartbeat")) {
      while (true) {
        delay(1.minutes)
        events.send(HeartbeatEvent)
      }
    }
    coroutineScope.launch(context = CoroutineName("ProjectViewPerformanceMonitor events")) {
      try {
        for (event in events) {
          processEvent(event)
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

  private fun processEvent(event: Event) {
    when (event) {
      is StartedEvent -> calculator.requestStarted(event.id, event.causes, event.startedTime)
      is LoadedEvent -> calculator.nodesLoaded(event.id, event.loadedCount)
      is FinishedEvent -> calculator.requestFinished(event.id, event.finishedTime)
      is HeartbeatEvent -> calculator.reportSample(TimeSource.Monotonic.markNow())
    }
  }

  private fun reportToFus(report: ProjectViewUpdateReport) {
    for (entry in report.updateCauseReports) {
      ProjectViewPerformanceCollector.logUpdated(
        entry.key,
        entry.value.loadedNodeCount,
        entry.value.timeSpent.inWholeMilliseconds,
      )
    }
    for (entry in report.stuckRequestReports) {
      ProjectViewPerformanceCollector.logStuckUpdateRequest(
        entry.key,
        entry.value.requestCount,
        entry.value.loadedNodeCount,
        entry.value.stuckFor.inWholeMilliseconds,
      )
    }
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
}

@ApiStatus.Internal
@VisibleForTesting
class ProjectViewUpdatePerformanceCalculator(
  private val processReport: (ProjectViewUpdateReport) -> Unit,
) {
  private var current = StatsSample()

  fun requestStarted(id: Long, causes: Collection<ProjectViewUpdateCause>, startedTime: ComparableTimeMark) {
    current.requestStarted(id, causes, startedTime)
  }

  fun nodesLoaded(id: Long, loadedCount: Int) {
    current.nodesLoaded(id, loadedCount)
  }

  fun requestFinished(id: Long, finishedTime: ComparableTimeMark) {
    current.requestFinished(id, finishedTime)
  }

  fun reportSample(reportTime: ComparableTimeMark) {
    val report = current.report(reportTime)
    if (report != null) {
      processReport(report)
    }
    current = current.startNewSample()
  }

  private class StatsSample {
    private var startedRequests = 0
    private var activeRequests = 0
    private var finishedRequests = 0
    private val requestStates = hashMapOf<Long, RequestState>()
    private val causeStates = hashMapOf<ProjectViewUpdateCause, CauseState>()
    private val causeReports = hashMapOf<ProjectViewUpdateCause, ProjectViewUpdateCauseReport>()

    fun requestStarted(id: Long, causes: Collection<ProjectViewUpdateCause>, startedTime: ComparableTimeMark) {
      requestStates[id] = RequestState(id, causes, startedTime)
      ++activeRequests
      ++startedRequests
      for (cause in causes) {
        val causeState = causeStates.getOrPut(cause) { CauseState(startedTime) }
        ++causeState.activeRequests
      }
    }

    fun nodesLoaded(id: Long, loadedCount: Int) {
      val requestState = requestStates[id]
      if (requestState == null) {
        LOG.warn(Throwable("Got an event for a request that doesn't exist: $id"))
        return
      }
      requestState.loadedNodeCount += loadedCount
      for (cause in requestState.causes) {
        val causeState = ensureNotNull(cause, causeStates[cause]) ?: continue
        causeState.loadedNodeCount += loadedCount
      }
    }

    fun requestFinished(id: Long, finishedTime: ComparableTimeMark) {
      val requestState = requestStates.remove(id)
      if (requestState == null) {
        LOG.warn(Throwable("Got an event for a request that doesn't exist: $id"))
        return
      }
      --activeRequests
      ++finishedRequests
      requestState.finishedTime = finishedTime
      for (cause in requestState.causes) {
        val causeState = ensureNotNull(cause, causeStates[cause]) ?: continue
        --causeState.activeRequests
        ++causeState.completedRequests
        if (causeState.activeRequests == 0) {
          allRequestsForCauseFinished(cause, finishedTime)
        }
      }
    }

    private fun allRequestsForCauseFinished(cause: ProjectViewUpdateCause, finishedTime: ComparableTimeMark) {
      val causeState = ensureNotNull(cause, causeStates.remove(cause)) ?: return
      val causeReport = causeReports.getOrPut(cause) { ProjectViewUpdateCauseReport() }
      causeReport.completedRequests += causeState.completedRequests
      causeReport.loadedNodeCount += causeState.loadedNodeCount
      causeReport.timeSpent += finishedTime - causeState.startedTime
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

    fun report(time: ComparableTimeMark): ProjectViewUpdateReport? {
      if (startedRequests == 0 && activeRequests == 0 && finishedRequests == 0) return null // don't spam when the IDE is idle
      val report = computeReport(time)
      reportToDebugLog(report)
      return report
    }

    private fun computeReport(time: ComparableTimeMark): ProjectViewUpdateReport {
      return ProjectViewUpdateReport(
        ProjectViewUpdateStatsReport(
          startedRequests,
          activeRequests,
          finishedRequests,
        ),
        causeReports,
        computeStuckRequestReports(time),
      )
    }

    private fun computeStuckRequestReports(time: ComparableTimeMark): HashMap<ProjectViewUpdateCause, ProjectViewUpdateStuckRequestReport> {
      val reports = hashMapOf<ProjectViewUpdateCause, ProjectViewUpdateStuckRequestReport>()
      for (requestState in requestStates.values) {
        if (!requestState.isStuck) continue
        for (cause in requestState.causes) {
          val report = reports.getOrPut(cause) {
            ProjectViewUpdateStuckRequestReport(
              sinceTime = requestState.startTime,
              reportTime = time,
            )
          }
          report.sinceTime = min(report.sinceTime, requestState.startTime)
          ++report.requestCount
          report.loadedNodeCount += requestState.loadedNodeCount
        }
      }
      return reports
    }

    fun startNewSample(): StatsSample {
      val newSample = StatsSample()
      newSample.activeRequests = activeRequests
      newSample.requestStates.putAll(requestStates)
      newSample.requestStates.values.forEach {
        it.isStuck = true // will be reported next time
      }
      newSample.causeStates.putAll(causeStates)
      // reports are not copied, as they're already reported
      return newSample
    }
  }

  private data class RequestState(
    val id: Long,
    val causes: Collection<ProjectViewUpdateCause>,
    val startTime: ComparableTimeMark,
    var loadedNodeCount: Int = 0,
    var finishedTime: ComparableTimeMark? = null,
    var isStuck: Boolean = false,
  )

  private data class CauseState(
    val startedTime: ComparableTimeMark,
    var activeRequests: Int = 0,
    var completedRequests: Int = 0,
    var loadedNodeCount: Int = 0,
  )
}

private fun reportToDebugLog(report: ProjectViewUpdateReport) {
  if (LOG.isDebugEnabled) {
    val stuckRequests = report.stuckRequestReports.size
    LOG.debug(
      "Project View performance sample: " +
      "started requests = ${report.stats.startedRequests}, " +
      "finished requests = ${report.stats.finishedRequests}, " +
      "still active requests = ${report.stats.activeRequests}, " +
      "stuck requests = ${stuckRequests}"
    )
    if (report.updateCauseReports.isNotEmpty()) {
      LOG.debug("Finished requests by cause:")
      for (entry in report.updateCauseReports.entries.sortedBy { it.key }) {
        LOG.debug("${entry.key}: ${entry.value}")
      }
    }
    if (report.stuckRequestReports.isNotEmpty()) {
      LOG.debug("Active requests by cause:")
      for (entry in report.updateCauseReports.entries.sortedBy { it.key }) {
        LOG.debug("${entry.key}: ${entry.value}")
      }
    }
  }
}

@ApiStatus.Internal
@VisibleForTesting
data class ProjectViewUpdateReport(
  val stats: ProjectViewUpdateStatsReport,
  val updateCauseReports: Map<ProjectViewUpdateCause, ProjectViewUpdateCauseReport>,
  val stuckRequestReports: Map<ProjectViewUpdateCause, ProjectViewUpdateStuckRequestReport>,
)

@ApiStatus.Internal
@VisibleForTesting
data class ProjectViewUpdateStatsReport(val startedRequests: Int, val activeRequests: Int, val finishedRequests: Int)

@ApiStatus.Internal
@VisibleForTesting
data class ProjectViewUpdateCauseReport(
  var completedRequests: Int = 0,
  var loadedNodeCount: Int = 0,
  var timeSpent: Duration = Duration.ZERO,
)

@ApiStatus.Internal
@VisibleForTesting
data class ProjectViewUpdateStuckRequestReport(
  var sinceTime: ComparableTimeMark,
  var reportTime: ComparableTimeMark,
  var requestCount: Int = 0,
  var loadedNodeCount: Int = 0,
) {
  val stuckFor: Duration
    get() = reportTime - sinceTime
}

private val LOG = logger<ProjectViewPerformanceMonitor>()
