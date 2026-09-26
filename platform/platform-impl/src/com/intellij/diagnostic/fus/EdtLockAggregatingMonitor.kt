// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic.fus

import com.intellij.openapi.application.ReadActionListener
import com.intellij.openapi.application.WriteActionListener
import com.intellij.openapi.application.WriteIntentReadActionListener
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.components.Service
import com.intellij.util.application
import com.intellij.util.ui.EDT
import org.HdrHistogram.Histogram
import org.HdrHistogram.SingleWriterRecorder
import org.jetbrains.annotations.ApiStatus
import kotlin.time.TimeSource

@Service
@ApiStatus.Internal
class EdtLockAggregatingMonitor {
  private val listener = EdtLockAggregatingListener()

  init {
    ApplicationManagerEx.getApplicationEx().addReadActionListener(listener, application)
    ApplicationManagerEx.getApplicationEx().addWriteActionListener(listener, application)
    ApplicationManagerEx.getApplicationEx().addWriteIntentReadActionListener(listener, application)
  }

  var writeLockWaitingHistogram: Histogram? = null
  var writeLockExecutionHistogram: Histogram? = null
  var readingLockWaitingHistogram: Histogram? = null
  var readingLockExecutionHistogram: Histogram? = null

  @ApiStatus.Internal
  data class AggregatedLockDistribution(
    val writeLockWaitingTimes: Histogram,
    val writeLockExecutionTimes: Histogram,
    val readingLockWaitingTimes: Histogram,
    val readingLockExecutionTimes: Histogram,
  )

  fun exportHistograms(): AggregatedLockDistribution {
    synchronized(this) {
      writeLockWaitingHistogram = listener.exportHistogram(writeLockWaitingHistogram, isRead = false, isWaiting = true)
      writeLockExecutionHistogram = listener.exportHistogram(writeLockExecutionHistogram, isRead = false, isWaiting = false)
      readingLockWaitingHistogram = listener.exportHistogram(readingLockWaitingHistogram, isRead = true, isWaiting = true)
      readingLockExecutionHistogram = listener.exportHistogram(readingLockExecutionHistogram, isRead = true, isWaiting = false)
      return AggregatedLockDistribution(writeLockWaitingHistogram!!, writeLockExecutionHistogram!!,
                                        readingLockWaitingHistogram!!, readingLockExecutionHistogram!!)
    }
  }
}

private class EdtLockAggregatingListener : WriteActionListener, ReadActionListener, WriteIntentReadActionListener {
  private data class Recorder(
    val waitingTimeRecorder: SingleWriterRecorder = SingleWriterRecorder(2),
    val executionTimeRecorder: SingleWriterRecorder = SingleWriterRecorder(2),
    var markOfAcquisitionIntent: TimeSource.Monotonic.ValueTimeMark? = null,
    var markOfExecutionIntent: TimeSource.Monotonic.ValueTimeMark? = null,
    var acquiredCounter: Int = 0,
  )

  private val readingLockRecorder = Recorder()
  private val writeLockRecorder = Recorder()


  override fun beforeWriteActionStart(action: Class<*>) {
    writeLockRecorder.beforeLockAcquisition()
  }

  override fun writeActionStarted(action: Class<*>) {
    writeLockRecorder.lockAcquired()
  }

  override fun writeActionFinished(action: Class<*>) {
    writeLockRecorder.lockReleased()
  }

  override fun beforeReadActionStart(action: Class<*>) {
    readingLockRecorder.beforeLockAcquisition()
  }

  override fun beforeWriteIntentReadActionStart(action: Class<*>) {
    readingLockRecorder.beforeLockAcquisition()
  }

  override fun readActionStarted(action: Class<*>) {
    readingLockRecorder.lockAcquired()
  }

  override fun writeIntentReadActionStarted(action: Class<*>) {
    readingLockRecorder.lockAcquired()
  }

  override fun writeIntentReadActionFinished(action: Class<*>) {
    readingLockRecorder.lockReleased()
  }

  override fun readActionFinished(action: Class<*>) {
    readingLockRecorder.lockReleased()
  }


  private fun Recorder.lockReleased() {
    if (!EDT.isCurrentThreadEdt()) {
      return
    }
    acquiredCounter -= 1
    if (acquiredCounter == 0) {
      val now = TimeSource.Monotonic.markNow()
      val executionIntentMark = markOfExecutionIntent
      if (executionIntentMark != null) {
        val timeOfExecution = now - executionIntentMark
        executionTimeRecorder.recordValue(timeOfExecution.inWholeNanoseconds)
      }
      markOfExecutionIntent = null
    }
  }

  private fun Recorder.beforeLockAcquisition() {
    if (!EDT.isCurrentThreadEdt()) {
      return
    }
    if (acquiredCounter == 0) {
      markOfAcquisitionIntent = TimeSource.Monotonic.markNow()
    }
  }

  private fun Recorder.lockAcquired() {
    if (!EDT.isCurrentThreadEdt()) {
      return
    }
    if (acquiredCounter == 0) {
      val now = TimeSource.Monotonic.markNow()
      val acquisitionIntentMark = markOfAcquisitionIntent
      if (acquisitionIntentMark != null) {
        val timeToWaitForAcquisition = now - acquisitionIntentMark
        waitingTimeRecorder.recordValue(timeToWaitForAcquisition.inWholeNanoseconds)
      }
      markOfAcquisitionIntent = null
      markOfExecutionIntent = now
    }
    acquiredCounter += 1
  }

  fun exportHistogram(existing: Histogram?, isRead: Boolean, isWaiting: Boolean): Histogram =
    when (isRead to isWaiting) {
      true to true -> readingLockRecorder.waitingTimeRecorder.getIntervalHistogram(existing)
      true to false -> readingLockRecorder.executionTimeRecorder.getIntervalHistogram(existing)
      false to true -> writeLockRecorder.waitingTimeRecorder.getIntervalHistogram(existing)
      false to false -> writeLockRecorder.executionTimeRecorder.getIntervalHistogram(existing)
      else -> error("unreachable")
    }
}