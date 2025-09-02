// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic.fus

import com.intellij.diagnostic.EventDurationHistogram
import com.intellij.diagnostic.PreciseEventWatcher
import com.intellij.diagnostic.UILatencyLogger
import com.intellij.openapi.components.service
import com.intellij.util.SystemProperties
import com.intellij.util.application
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.HdrHistogram.Histogram
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

@ApiStatus.Internal
class FusReportingEventWatcher : PreciseEventWatcher() {
  companion object {
    private val SAMPLING_PERIOD_S: Int = SystemProperties.getIntProperty("ui.responsiveness.flush.period.s", 3600)

    @Volatile
    private var _instance: FusReportingEventWatcher? = null

    @get:ApiStatus.Internal
    val instance: FusReportingEventWatcher? get() = _instance
  }

  init {
    // preload service so that it starts monitoring locks
    service<EdtLockAggregatingMonitor>()
    _instance = this
  }

  @Volatile
  private var samplingTimeStamp: Long = System.currentTimeMillis()

  @Suppress("OPT_IN_USAGE")
  private val samplingCoroutine = GlobalScope.launch {
    while (true) {
      delay(SAMPLING_PERIOD_S.seconds)
      reportAndFlushDataToFus()
    }
  }

  @ApiStatus.Internal
  fun reportAndFlushDataToFus(): Unit = reportAndFlushData { duration, currentAwtDispatchTimeHistogram, currentWaitingTimesHistogram, currentExecutionTimeHistogram, lockingHistograms ->
    UILatencyLogger.reportUiResponsiveness(
      duration,
      currentAwtDispatchTimeHistogram.toEventDurationHistogram(),
      currentWaitingTimesHistogram.toEventDurationHistogram(),
      currentExecutionTimeHistogram.toEventDurationHistogram(),
      lockingHistograms.writeLockWaitingTimes.toEventDurationHistogram(),
      lockingHistograms.writeLockExecutionTimes.toEventDurationHistogram(),
      lockingHistograms.readingLockWaitingTimes.toEventDurationHistogram(),
      lockingHistograms.readingLockExecutionTimes.toEventDurationHistogram())
  }

  @VisibleForTesting
  fun reportAndFlushData(consumer: (Duration, awtDispatchTimes: Histogram, waitingTimes: Histogram, executionTimes: Histogram, EdtLockAggregatingMonitor.AggregatedLockDistribution) -> Unit) {
    val currentTimestamp = System.currentTimeMillis()
    val duration = (currentTimestamp - samplingTimeStamp).milliseconds
    samplingTimeStamp = currentTimestamp

    val currentWaitingTimesHistogram: Histogram
    val currentExecutionTimeHistogram: Histogram
    val currentAwtDispatchTimeHistogram: Histogram

    // we use synchronization here to establish happens-before between mutating fields of histograms
    // the processing may happen on different threads backing the default dispatcher
    synchronized(this) {
      currentWaitingTimesHistogram = waitingTimeHistogram
      currentExecutionTimeHistogram = executionTimeHistogram
      // just flush the contents into /dev/null to avoid memory leak
      queueSizeHistogram
      currentAwtDispatchTimeHistogram = awtEventDispatchTimeHistogram
    }

    val lockingHistograms = application.service<EdtLockAggregatingMonitor>().exportHistograms()

    consumer(duration, currentAwtDispatchTimeHistogram, currentWaitingTimesHistogram, currentExecutionTimeHistogram, lockingHistograms)
  }

  private fun Histogram.toEventDurationHistogram(): EventDurationHistogram {
    val totalExecutionTime = recordedValues().sumOf { it.countAtValueIteratedTo * it.valueIteratedTo }
    return EventDurationHistogram(
      totalCount.toInt(),
      totalExecutionTime.nanoseconds,
      getValueAtPercentile(50.0).nanoseconds,
      getValueAtPercentile(95.0).nanoseconds,
      getValueAtPercentile(99.0).nanoseconds
    )
  }

  override fun dispose() {
    samplingCoroutine.cancel()
  }
}