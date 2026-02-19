// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import com.intellij.openapi.application.ApplicationListener
import com.intellij.openapi.application.ReadActionListener
import com.intellij.openapi.application.WriteIntentReadActionListener
import com.intellij.util.ui.EDT
import kotlin.time.Duration
import kotlin.time.TimeSource

/**
 * Tracks the load of EDT from the perspective of the locking infrastructure
 */
internal class EdtLockLoadListener : ApplicationListener, ReadActionListener, WriteIntentReadActionListener {

  private val epochStart = TimeSource.Monotonic.markNow()

  // These fields are not guarded by any thread safety mechanisms by design.
  // This listener is intended to work only on EDT, hence all operations with the mutable fields are serialized uniquely.
  private val writeLockDiagnosticData: LockDiagnosticData = LockDiagnosticData()
  private val readLockDiagnosticData: LockDiagnosticData = LockDiagnosticData()
  private val writeIntentReadLockDiagnosticData: LockDiagnosticData = LockDiagnosticData()

  data class LockDiagnosticData(
    var lockAcquiredCounter: Int = 0,
    var previousMark: TimeSource.Monotonic.ValueTimeMark? = null,
    var totalLockDuration: Duration = Duration.ZERO,
    var totalAcquisitionIntentDuration: Duration = Duration.ZERO,
  )

  override fun beforeWriteActionStart(action: Any) {
    if (!EDT.isCurrentThreadEdt()) {
      return
    }
    writeLockDiagnosticData.beforeLockAcquired()
  }

  override fun writeActionStarted(action: Any) {
    if (!EDT.isCurrentThreadEdt()) {
      return
    }
    writeLockDiagnosticData.lockAcquired()
  }

  override fun writeActionFinished(action: Any) {
    if (!EDT.isCurrentThreadEdt()) {
      return
    }
    writeLockDiagnosticData.lockReleased()
  }

  override fun beforeReadActionStart(action: Class<*>) {
    if (!EDT.isCurrentThreadEdt() || writeLockDiagnosticData.lockAcquiredCounter > 0 || writeIntentReadLockDiagnosticData.lockAcquiredCounter > 0) {
      // we don't want to count read actions within write lock
      return
    }
    readLockDiagnosticData.beforeLockAcquired()
  }

  override fun readActionStarted(action: Class<*>) {
    if (!EDT.isCurrentThreadEdt() || writeLockDiagnosticData.lockAcquiredCounter > 0 || writeIntentReadLockDiagnosticData.lockAcquiredCounter > 0) {
      // we don't want to count read actions within write lock
      return
    }
    readLockDiagnosticData.lockAcquired()
  }


  override fun readActionFinished(action: Class<*>) {
    if (!EDT.isCurrentThreadEdt() || writeLockDiagnosticData.lockAcquiredCounter > 0 || writeIntentReadLockDiagnosticData.lockAcquiredCounter > 0) {
      // we don't want to count read actions within write lock
      return
    }
    readLockDiagnosticData.lockReleased()
  }

  override fun beforeWriteIntentReadActionStart(action: Class<*>) {
    if (!EDT.isCurrentThreadEdt() || writeLockDiagnosticData.lockAcquiredCounter > 0) {
      // we don't want to count read actions within write lock
      return
    }
    writeIntentReadLockDiagnosticData.beforeLockAcquired()
  }

  override fun writeIntentReadActionStarted(action: Class<*>) {
    if (!EDT.isCurrentThreadEdt() || writeLockDiagnosticData.lockAcquiredCounter > 0) {
      // we don't want to count read actions within write lock
      return
    }
    writeIntentReadLockDiagnosticData.lockAcquired()
  }

  override fun writeIntentReadActionFinished(action: Class<*>) {
    if (!EDT.isCurrentThreadEdt() || writeLockDiagnosticData.lockAcquiredCounter > 0) {
      return
    }
    writeIntentReadLockDiagnosticData.lockReleased()
  }

  private fun LockDiagnosticData.beforeLockAcquired() {
    if (lockAcquiredCounter == 0) {
      previousMark = TimeSource.Monotonic.markNow()
    }
  }

  private fun LockDiagnosticData.lockAcquired() {
    if (lockAcquiredCounter == 0) {
      val now = TimeSource.Monotonic.markNow()
      val intentToAcquire = previousMark
      if (intentToAcquire != null) {
        val timeToWaitForAcquisition = now - intentToAcquire
        totalAcquisitionIntentDuration += timeToWaitForAcquisition
      }
      previousMark = now
    }
    lockAcquiredCounter += 1
  }

  private fun LockDiagnosticData.lockReleased() {
    lockAcquiredCounter -= 1
    if (lockAcquiredCounter == 0) {
      val actionStart = requireNotNull(previousMark) {
        "lockReleased() must be preceded by lockAcquired()"
      }
      previousMark = null
      val writeActionEnd = TimeSource.Monotonic.markNow()
      val difference = writeActionEnd.minus(actionStart)
      totalLockDuration += difference
    }
  }

  fun export(): EdtLockLoadMonitorService.MeasurementData {
    if (!EDT.isCurrentThreadEdt()) {
      throw IllegalStateException("Must be called on EDT; the values in this class are not thread-safe")
    }
    val epochDuration = TimeSource.Monotonic.markNow().minus(epochStart)
    val measurementData = EdtLockLoadMonitorService.MeasurementData(
      totalDuration = epochDuration,
      timeToWriteLockDuration = writeLockDiagnosticData.totalAcquisitionIntentDuration,
      writeLockDurationTotal = writeLockDiagnosticData.totalLockDuration,
      timeToReadLockDuration = readLockDiagnosticData.totalAcquisitionIntentDuration,
      readLockDurationTotal = readLockDiagnosticData.totalLockDuration,
      timeToWriteIntentLockDuration = writeIntentReadLockDiagnosticData.totalAcquisitionIntentDuration,
      writeIntentLockDurationTotal = writeIntentReadLockDiagnosticData.totalLockDuration
    )
    return measurementData
  }
}