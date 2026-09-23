// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl

import com.intellij.concurrency.SensitiveProgressWrapper
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ex.ApplicationEx
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicatorProvider
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.openapi.progress.util.ProgressWrapper
import com.intellij.util.ExceptionUtil
import com.intellij.util.TimeoutUtil
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.indexing.UnindexedFilesUpdater
import org.jetbrains.annotations.ApiStatus.Internal
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.Future
import java.util.concurrent.FutureTask
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * The general-purpose part of the scanning thread pool: runs a workload on all scanning threads in parallel.
 *
 * This part has no dependency on the indexing implementation, so it stays in `intellij.platform.lang.impl` and can be
 * used by non-indexing clients (e.g. `FindInProjectTask`, `PushedFilePropertiesUpdaterImpl`). The indexing-specific
 * `FilesScanExecutor.processFilesInScope` delegates here and is free to move to a backend module.
 */
@Internal
object ScanningExecutor {
  private val THREAD_COUNT = (UnindexedFilesUpdater.getNumberOfScanningThreads() - 1).coerceAtLeast(1)
  private val ourExecutor = AppExecutorUtil.createBoundedApplicationPoolExecutor("Scanning", THREAD_COUNT)

  @JvmStatic
  fun runOnAllThreads(runnable: Runnable) {
    val progress = ProgressIndicatorProvider.getGlobalProgressIndicator()
    if (!ApplicationManager.getApplication().isUnitTestMode) {
      checkNotNull(progress) { "progress indicator is required" }
    }
    val results = ArrayList<Future<*>>()
    for (i in 0 until THREAD_COUNT) {
      // The current Job cancellation will NOT stop the pooled threads. Use 'coroutineToIndicator'.
      results.add(ourExecutor.submit { ProgressManager.getInstance().runProcess(runnable, ProgressWrapper.wrap(progress)) })
    }

    // put the current thread to work too so the total thread count is `getNumberOfScanningThreads`
    // and avoid thread starvation due to a recursive `runOnAllThreads` invocation
    runnable.run()
    for (result in results) {
      // complete the future to avoid waiting for it forever if `ourExecutor` is fully booked
      (result as FutureTask<*>).run()
      ProgressIndicatorUtils.awaitWithCheckCanceled(result)
    }
  }

  @JvmStatic
  fun <T: Any> processOnAllThreadsInReadActionWithRetries(deque: ConcurrentLinkedDeque<T>, consumer: (T) -> Boolean): Boolean {
    return doProcessOnAllThreadsInReadAction(deque, consumer, true)
  }

  @JvmStatic
  fun <T: Any> processOnAllThreadsInReadActionNoRetries(deque: ConcurrentLinkedDeque<T>, consumer: (T) -> Boolean): Boolean {
    return doProcessOnAllThreadsInReadAction(deque, consumer, false)
  }

  private fun <T: Any> doProcessOnAllThreadsInReadAction(deque: ConcurrentLinkedDeque<T>,
                                                         consumer: (T) -> Boolean,
                                                         retryCanceled: Boolean): Boolean {
    val application = ApplicationManager.getApplication() as ApplicationEx
    // resolved once per scan, not per item, so the per-item cost stays at one map write
    val scanningWorkTracker = ScanningWorkTracker.getInstance()
    return processOnAllThreads(deque) { o ->
      if (application.isReadAccessAllowed) {
        return@processOnAllThreads consumer(o)
      }

      var result = true
      val indicator = ProgressIndicatorProvider.getGlobalProgressIndicator()

      // `wrapper` is what a pending write action cancels to abort this read action, so `ScanningCancellationMonitor`
      // needs it to tell whether that cancellation happened and to repair it if it did not take effect
      val wrapper = indicator?.let { SensitiveProgressWrapper(it) }

      val action = Runnable {
        scanningWorkTracker.trackReadAction(wrapper) {
          result = consumer(o)
        }
      }

      if (!ProgressIndicatorUtils.runInReadActionWithWriteActionPriority(action, wrapper)) {
        throw if (retryCanceled) ProcessCanceledException() else StopWorker()
      }
      else {
        return@processOnAllThreads result
      }
    }
  }

  private fun <T: Any> processOnAllThreads(deque: ConcurrentLinkedDeque<T>, processor: (T) -> Boolean): Boolean {
    ProgressManager.checkCanceled()
    if (deque.isEmpty()) {
      return true
    }
    val runnersCount = AtomicInteger()
    val idleCount = AtomicInteger()
    val error = AtomicReference<Throwable?>()
    val stopped = AtomicBoolean()
    val exited = AtomicBoolean()
    runOnAllThreads {
      runnersCount.incrementAndGet()
      var idle = false
      try {
        while (!stopped.get()) {
          ProgressManager.checkCanceled()
          if (deque.peek() == null) {
            if (!idle) {
              idle = true
              idleCount.incrementAndGet()
            }
          }
          else if (idle) {
            idle = false
            idleCount.decrementAndGet()
          }
          if (idle) {
            if (idleCount.get() == runnersCount.get() && deque.isEmpty()) break
            TimeoutUtil.sleep(1L)
            continue
          }
          val item = deque.poll() ?: continue
          try {
            if (!processor(item)) {
              stopped.set(true)
            }
            if (exited.get() && !stopped.get()) {
              throw AssertionError("early exit")
            }
          }
          catch (ex: StopWorker) {
            deque.addFirst(item)
            return@runOnAllThreads
          }
          catch (ex: ProcessCanceledException) {
            deque.addFirst(item)
          }
          catch (ex: Throwable) {
            error.compareAndSet(null, ex)
          }
        }
        exited.set(true)
        if (!deque.isEmpty() && !stopped.get()) {
          throw AssertionError("early exit")
        }
      }
      finally {
        if (idle) {
          idleCount.decrementAndGet()
        }
        runnersCount.decrementAndGet()
      }
    }
    ExceptionUtil.rethrowAllAsUnchecked(error.get())
    return !stopped.get()
  }
}

private class StopWorker : ProcessCanceledException()
