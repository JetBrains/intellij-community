// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ThreadingSupport
import com.intellij.openapi.application.WriteDelayDiagnostics
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.application.impl.ApplicationImpl
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.Logger
import com.intellij.platform.locking.impl.listeners.LockAcquisitionListener
import com.intellij.util.asDisposable
import com.intellij.util.concurrency.AppExecutorUtil
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

@ApiStatus.Internal
class WriteLockMeasurerImpl(scope: CoroutineScope) : WriteLockMeasurer {
  init {
    val application = ApplicationManager.getApplication()
    if (application is ApplicationImpl) {
      application.addLockAcquisitionListener(WriteLockMeasurementListener(), scope.asDisposable())
    }
  }
}

private class WriteLockMeasurementListener : LockAcquisitionListener<WriteLockMeasurementListener.LockAcquisitionResult> {

  // defer reading isUnitTest flag until it's initialized
  private object Holder {
    val ourDumpThreadsOnLongWriteActionWaiting: Int = if (ApplicationManager.getApplication().isUnitTestMode()) 0 else Integer.getInteger("dump.threads.on.long.write.action.waiting", 0)
  }

  data class LockAcquisitionResult(val acquisitionMoment: /* milliseconds */ Long, val reportingFuture: Future<*>?)


  override fun beforeWriteLockAcquired(): LockAcquisitionResult {
    val delay = Holder.ourDumpThreadsOnLongWriteActionWaiting
    val reportSlowWrite: Future<*>? = if (delay <= 0 || PerformanceWatcher.getInstanceIfCreated() == null) null
    else AppExecutorUtil.getAppScheduledExecutorService()
      .scheduleWithFixedDelay({
                                val path = PerformanceWatcher.getInstance().dumpThreads("waiting", true, true)
                                if (path != null && ApplicationManagerEx.isInIntegrationTest()) {
                                  val message = "Long write action takes more than ${Holder.ourDumpThreadsOnLongWriteActionWaiting}ms, details saved to $path"
                                  logger.error(message)
                                }
                              },
                              delay.toLong(), delay.toLong(), TimeUnit.MILLISECONDS)
    val t = System.currentTimeMillis()
    return LockAcquisitionResult(t, reportSlowWrite)
  }

  override fun afterWriteLockAcquired(beforeResult: LockAcquisitionResult) {
    val (acquisitionMoment, reportSlowWrite) = beforeResult
    val elapsed = System.currentTimeMillis() - acquisitionMoment
    try {
      WriteDelayDiagnostics.registerWrite(elapsed)
    }
    catch (thr: Throwable) {
      // we can be canceled here, it is an expected behavior
      if (thr !is ControlFlowException) {
        // Warn instead of error to avoid breaking acquiring the lock
        logger.warn("Failed to register write lock in diagnostics service", thr)
      }
    }
    if (logger.isDebugEnabled) {
      if (elapsed != 0L) {
        logger.debug("Write action wait time: $elapsed")
      }
    }
    reportSlowWrite?.cancel(false)
  }

}

private val logger = Logger.getInstance(ThreadingSupport::class.java)
