// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic.fus

import com.intellij.diagnostic.UILatencyLogger
import com.intellij.diagnostic.fus.EdtFreezeChainMonitor.Companion.CHAIN_MONITOR_WINDOW_MS
import com.intellij.openapi.application.*
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.application.impl.LaterInvocator
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.util.SystemProperties
import com.intellij.util.asDisposable
import com.intellij.util.ui.EDT
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.util.*
import java.util.concurrent.ConcurrentSkipListSet
import kotlin.math.min
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds

/**
 * The EDT is used to execute a lot of computations sequentially
 * Sometimes these computations are not short-living, and they provoke a UI freeze.
 * One important thing here is that several micro-freezes that happen in order with a little pause between them
 * give an impression that the UI is lagging -- the user cannot scroll quickly, and they experience decreased responsiveness.
 *
 * We call such an event _a freeze chain_. A freeze chain occurs when there is a sequence of short-living locking (i.e., read or write lock) actions.
 * Formally, a freeze chain starts when the EDT is blocked for at least N (e.g. 50) ms in a W (e.g. 100) ms time window.
 * Then the window starts extending, recording all time frames where EDT is blocked and registering them in the started freeze chain.
 * The freeze chain ends if the last W ms of it was not busy with locks for more than N ms.
 * If a freeze chain lasted at least L (e.g. 1000) ms, it gets reported to logs (most likely to FUS).
 *
 * Modality states are excluded from freeze chains -- we are interested in the responsiveness of the plain IDE.
 */
@ApiStatus.Internal
@Service
class EdtFreezeChainMonitor(val scope: CoroutineScope) {

  data class FreezeChainReport(
    val durationNs: Long,
    val totalReadNs: Long,
    val totalWriteNs: Long,
    val totalReadOps: Int,
    val totalWriteOps: Int,
  )

  companion object {
    private val CHAIN_MONITOR_WINDOW_MS = SystemProperties.getIntProperty("ui.freeze.chain.window.ms", 100)
    private val CHAIN_END_THRESHOLD_MS = SystemProperties.getIntProperty("ui.freeze.chain.threshold.ms", 50)
    private val REPORTABLE_CHAIN_DURATION_MS = SystemProperties.getIntProperty("ui.freeze.chain.reportable.duration.ms", 1000)
    private val PRIORITY_QUEUE_MAX_SIZE = SystemProperties.getIntProperty("ui.freeze.chain.priority.queue.max.size", 1_000)
    private val FUS_REPORTING_DELAY = SystemProperties.getIntProperty("ui.freeze.chain.fus.reporting.delay.ms", 120_000)

    @JvmStatic
    @TestOnly
    @Volatile
    var testingConfig: TestingConfig? = null
  }

  @TestOnly
  data class TestingConfig(val windowMs: Int, val thresholdMs: Int, val reportableMs: Int) {

    @field:ApiStatus.Internal
    private val testingLastReport: MutableList<FreezeChainReport> = mutableListOf()

    fun recordReport(report: FreezeChainReport) {
      synchronized(testingLastReport) {
        testingLastReport.add(report)
      }
    }

    fun drainReports(): List<FreezeChainReport> {
      synchronized(testingLastReport) {
        val reports = ArrayList(testingLastReport)
        testingLastReport.clear()
        return reports
      }
    }
  }

  private fun windowMs(): Int = (testingConfig?.windowMs ?: CHAIN_MONITOR_WINDOW_MS)
  private fun thresholdMs(): Int = (testingConfig?.thresholdMs ?: CHAIN_END_THRESHOLD_MS)
  private fun reportableMs(): Int = (testingConfig?.reportableMs ?: REPORTABLE_CHAIN_DURATION_MS)

  // We compute freeze chains from precise EDT lock intervals using our own listener.
  private val listener = FreezeChainLockListener()

  // We need to carefully debounce data to avoid reporting too much data
  // We decided to store freeze chains in a bounded priority queue and report the most severe chain each two minutes.
  // The queue is bounded, i.e. we remove the minimal element if there are too many reports.
  private val priorityQueue: SortedSet<FreezeChainReport> = ConcurrentSkipListSet(compareBy { it.durationNs })

  init {
    // Register listeners to get intervals on EDT for read/write/write-intent
    val disposable = scope.asDisposable()
    ApplicationManagerEx.getApplicationEx().addReadActionListener(listener, disposable)
    ApplicationManagerEx.getApplicationEx().addWriteActionListener(listener, disposable)
    ApplicationManagerEx.getApplicationEx().addWriteIntentReadActionListener(listener, disposable)
    LaterInvocator.addModalityStateListener(listener, disposable)
    launchWakeupCoroutine()
    launchReportingCoroutine()
  }

  /**
   * We want to periodically check if the reporting window is over without acquisition of lock
   * For this purpose we launch a separate coroutine every [CHAIN_MONITOR_WINDOW_MS] and try to check the latest load of EDT
   */
  fun launchWakeupCoroutine() {
    scope.launch(Dispatchers.UI + ModalityState.any().asContextElement()) {
      while (true) {
        val nowNs = System.nanoTime()
        checkForChainEnd(nowNs)
        delay(windowMs().milliseconds)
      }
    }
  }

  fun launchReportingCoroutine() {
    scope.launch(Dispatchers.IO) {
      while (true) {
        try {
          val topReport = priorityQueue.removeLast()
          UILatencyLogger.reportFreezeChain(
            totalDuration = topReport.durationNs.nanoseconds,
            readingDuration = topReport.totalReadNs.nanoseconds,
            writingDuration = topReport.totalWriteNs.nanoseconds,
            readingOperations = topReport.totalReadOps,
            writingOperations = topReport.totalWriteOps,
          )
        }
        catch (_: NoSuchElementException) {
          // ignored, no freeze chains to report
        }
        delay(FUS_REPORTING_DELAY.milliseconds)
      }
    }
  }

  private data class Phase(var startNs: Long = -1L)
  private data class State(
    var acquiredCount: Int = 0,
    val waiting: Phase = Phase(),
    val execution: Phase = Phase(),
  )

  private inner class FreezeChainLockListener : WriteActionListener, ReadActionListener, WriteIntentReadActionListener, ModalityStateListener {

    // we want to record data only non-modal freeze chains
    // because modality blocks UI anyway, so freezes there are more or less expected
    private var modalityCounter: Int = 0

    private val readState = State()
    private val writeState = State()

    override fun beforeWriteActionStart(action: Class<*>) = beforeAcquire(writeState)
    override fun writeActionStarted(action: Class<*>) = onAcquired(writeState)
    override fun writeActionFinished(action: Class<*>) = onReleased(writeState)

    override fun beforeReadActionStart(action: Class<*>) = beforeAcquire(readState)
    override fun readActionStarted(action: Class<*>) = onAcquired(readState)
    override fun readActionFinished(action: Class<*>) = onReleased(readState)
    override fun beforeWriteIntentReadActionStart(action: Class<*>) = beforeAcquire(readState)
    override fun writeIntentReadActionStarted(action: Class<*>) = onAcquired(readState)
    override fun writeIntentReadActionFinished(action: Class<*>) = onReleased(readState)

    override fun beforeModalityStateChanged(entering: Boolean, modalEntity: Any) {
      if (entering) {
        if (modalityCounter == 0) {
          onReleased(readState)
        }
        modalityCounter++
      }
      else {
        modalityCounter--
        if (modalityCounter == 0) {
          onAcquired(readState)
        }
      }
    }

    private fun nowNs(): Long = System.nanoTime()

    private fun beforeAcquire(state: State) {
      if (!EDT.isCurrentThreadEdt() || modalityCounter > 0) return
      if (state.acquiredCount == 0 && state.waiting.startNs == -1L) {
        state.waiting.startNs = nowNs()
      }
    }

    private fun onAcquired(state: State) {
      if (!EDT.isCurrentThreadEdt() || modalityCounter > 0) return
      val n = nowNs()
      if (state.acquiredCount == 0) {
        // close waiting interval (if any), feed to chain detector
        if (state.waiting.startNs != -1L) {
          val kind = if (state === writeState) LockKind.WRITE else LockKind.READ
          onBlockedInterval(kind, state.waiting.startNs, n)
          state.waiting.startNs = -1L
        }
        // open execution interval
        state.execution.startNs = n
        // top-level acquisition begins: if chain is active, bump ops counter
        if (chainActive) {
          if (state === writeState) totalWriteOps++ else totalReadOps++
        }
      }
      state.acquiredCount++
    }

    private fun onReleased(state: State) {
      if (!EDT.isCurrentThreadEdt() || modalityCounter > 0) return
      if (state.acquiredCount <= 0) return
      state.acquiredCount--
      if (state.acquiredCount == 0) {
        val n = nowNs()
        val execStart = state.execution.startNs
        if (execStart != -1L) {
          val kind = if (state === writeState) LockKind.WRITE else LockKind.READ
          onBlockedInterval(kind, execStart, n)
          state.execution.startNs = -1L
        }
        // also mark an idle check to possibly close a chain if nothing happens further; handled by sliding window logic on next tick/event
        checkForChainEnd(n)
      }
    }
  }

  // Sliding-window freeze-chain detector below
  private enum class LockKind { READ, WRITE }
  private data class Segment(val kind: LockKind, val startNs: Long, val endNs: Long) {
    fun length(): Long = endNs - startNs
  }

  private val readSegments: ArrayDeque<Pair<Segment, /* actual length*/ Long>> = ArrayDeque()
  private val writeSegments: ArrayDeque<Segment> = ArrayDeque()

  // is IDE currently in freeze chain
  private var chainActive: Boolean = false

  // timestamp when a chain started
  private var chainStartNs: Long = 0L

  // total time spent on waiting and executing write actions
  private var totalWriteInChainNs: Long = 0L

  // total time spent on waiting and executing read and write-intent actions
  private var totalReadInChainNs: Long = 0L

  // total number of write operations in a chain
  private var totalWriteOps: Int = 0

  // total number of read and write-intent operations in chain
  private var totalReadOps: Int = 0

  /**
   * Handle the incoming interval
   */
  private fun onBlockedInterval(kind: LockKind, startNs: Long, endNs: Long) {
    if (endNs <= startNs) return
    // record segment
    addSegment(Segment(kind, startNs, endNs))
    // if a chain is active, accumulate time right away
    if (chainActive) {
      val delta = endNs - startNs
      when (kind) {
        LockKind.WRITE -> totalWriteInChainNs += delta
        LockKind.READ -> totalReadInChainNs += delta
      }
    }
    // maintain sliding window condition and chain state
    updateChain(endNs)
  }

  private fun checkForChainEnd(nowNs: Long) {
    // If idle for WINDOW_MS, force evaluation; the updateChain will see last 100ms has <THRESHOLD and close
    updateChain(nowNs)
  }

  private fun pruneOld(nowNs: Long) {
    val windowNs = windowMs() * 1_000_000L
    val cutoff = nowNs - windowNs
    while (!readSegments.isEmpty()) {
      val (topSegment, realLength) = readSegments.first
      if (topSegment.endNs <= cutoff) {
        lastWindowDurationNs -= realLength
        lastWindowReadDurationNs -= topSegment.length()
        readSegments.removeFirst()
      }
      else {
        break
      }
    }
    while (!writeSegments.isEmpty()) {
      val topSegment = writeSegments.first
      if (topSegment.endNs <= cutoff) {
        lastWindowDurationNs -= topSegment.length()
        lastWindowWriteDurationNs -= topSegment.length()
        writeSegments.removeFirst()
      }
      else {
        break
      }
    }
  }

  data class WindowBlocked(val actualStartNs: Long, val report: FreezeChainReport)

  private var lastWindowDurationNs = 0L
  private var lastWindowReadDurationNs = 0L
  private var lastWindowWriteDurationNs = 0L

  private fun addSegment(segment: Segment) {
    when (segment.kind) {
      LockKind.READ -> {
        var resultLength = segment.length()
        val counter = writeSegments.descendingIterator()
        while (counter.hasNext()) {
          val writeSegment = counter.next()
          if (writeSegment.startNs >= segment.startNs) {
            resultLength -= writeSegment.length()
          }
          else {
            break
          }
        }
        lastWindowDurationNs += resultLength
        lastWindowReadDurationNs += segment.length()
        readSegments.addLast(segment to resultLength)
      }
      LockKind.WRITE -> {
        lastWindowDurationNs += segment.length()
        lastWindowWriteDurationNs += segment.length()
        writeSegments.addLast(segment)
      }
    }
  }

  fun recordReportForFus(report: FreezeChainReport) {
    while (priorityQueue.size >= PRIORITY_QUEUE_MAX_SIZE) {
      priorityQueue.removeFirst()
    }
    priorityQueue.add(report)
  }

  private fun updateChain(nowNs: Long) {
    pruneOld(nowNs)
    val blockedInLastWindowNs = lastWindowDurationNs
    val thresholdNs = thresholdMs() * 1_000_000L
    if (!chainActive) {
      if (blockedInLastWindowNs >= thresholdNs) {
        chainActive = true
        chainStartNs = min(writeSegments.peekFirst()?.startNs ?: Long.MAX_VALUE, readSegments.peekFirst()?.first?.startNs
                                                                                 ?: Long.MAX_VALUE) // the start of the chain is the start of busy segment
        // reset per-chain aggregates at the start
        totalWriteInChainNs = lastWindowWriteDurationNs
        totalReadInChainNs = lastWindowReadDurationNs
        totalWriteOps = writeSegments.size
        totalReadOps = readSegments.size
      }
    }
    else {
      if (blockedInLastWindowNs < thresholdNs) {
        // chain ends; also end if there were no events for a full window already captured by drop in blockedNs
        val duration = (nowNs - chainStartNs).nanoseconds
        if (duration.inWholeMilliseconds >= reportableMs()) { // avoid spurious tiny chains
          thisLogger().trace("Freeze chain detected: $duration (Read duration: ${totalReadInChainNs.nanoseconds}, $totalReadOps operations, Write duration: ${totalWriteInChainNs.nanoseconds}, $totalWriteOps operations)")
          val report = FreezeChainReport(
            durationNs = nowNs - chainStartNs,
            totalReadNs = totalReadInChainNs,
            totalWriteNs = totalWriteInChainNs,
            totalReadOps = totalReadOps,
            totalWriteOps = totalWriteOps,
          )

          recordReportForFus(report)

          testingConfig?.apply {
            recordReport(report)
          }
        }
        chainActive = false
      }
    }
  }
}