// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ex.ApplicationEx
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.util.AwaitCancellationAndInvoke
import com.intellij.util.awaitCancellationAndInvoke
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import kotlin.time.Duration

@OptIn(AwaitCancellationAndInvoke::class)
@Service
@ApiStatus.Internal
class EdtLockLoadMonitorService(scope: CoroutineScope) {
  private val listener = EdtLockLoadListener()

  init {
    scope.awaitCancellationAndInvoke {
      val data = export()
      logger<EdtLockLoadMonitorService>().info("EDT lock load monitor data:\n" +
                                               "${data.readLockDurationRatio * 100}% read lock load (lock acquisition duration: ${data.timeToReadLockDuration}; lock held duration: ${data.readLockDurationTotal});\n" +
                                               "${data.writeLockDurationRatio * 100}% write lock load (total acquisition duration: ${data.timeToWriteLockDuration}; lock held duration: ${data.writeLockDurationTotal});\n" +
                                               "${data.writeIntentLockDurationRatio * 100}% write intent lock load (total acquisition duration: ${data.timeToWriteIntentLockDuration}; lock held duration: ${data.writeIntentLockDurationTotal});")
    }
  }

  fun initialize() {
    val application = ApplicationManager.getApplication()
    if (application !is ApplicationEx) {
      thisLogger().info("EDT lock load monitor is disabled: application is not an instance of ApplicationEx")
      return
    }
    with(application) {
      application.addApplicationListener(listener, application)
      application.addReadActionListener(listener, application)
      application.addWriteIntentReadActionListener(listener, application)
    }
  }

  @ApiStatus.Internal
  data class MeasurementData(
    val totalDuration: Duration,
    val timeToWriteLockDuration: Duration,
    val writeLockDurationTotal: Duration,
    val timeToReadLockDuration: Duration,
    val readLockDurationTotal: Duration,
    val timeToWriteIntentLockDuration: Duration,
    val writeIntentLockDurationTotal: Duration,
  ) {
    val writeLockDurationRatio: Double
      get() = (writeLockDurationTotal + timeToWriteLockDuration).inWholeNanoseconds.toDouble() / totalDuration.inWholeNanoseconds

    val readLockDurationRatio: Double
      get() = (readLockDurationTotal + timeToReadLockDuration).inWholeNanoseconds.toDouble() / totalDuration.inWholeNanoseconds

    val writeIntentLockDurationRatio: Double
      get() = (writeIntentLockDurationTotal + timeToWriteIntentLockDuration).inWholeNanoseconds.toDouble() / totalDuration.inWholeNanoseconds

    operator fun minus(other: MeasurementData): MeasurementData = MeasurementData(
      totalDuration = totalDuration - other.totalDuration,
      timeToWriteLockDuration = timeToWriteLockDuration - other.timeToWriteLockDuration,
      writeLockDurationTotal = writeLockDurationTotal - other.writeLockDurationTotal,
      timeToReadLockDuration = timeToReadLockDuration - other.timeToReadLockDuration,
      readLockDurationTotal = readLockDurationTotal - other.readLockDurationTotal,
      timeToWriteIntentLockDuration = timeToWriteIntentLockDuration - other.timeToWriteIntentLockDuration,
      writeIntentLockDurationTotal = writeIntentLockDurationTotal - other.writeIntentLockDurationTotal,
    )
  }

  suspend fun export(): MeasurementData {
    return withContext(Dispatchers.EDT) {
      listener.export()
    }
  }
}