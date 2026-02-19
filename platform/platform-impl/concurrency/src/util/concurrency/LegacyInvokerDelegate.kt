// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.concurrency

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.util.ThreeState
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import java.awt.EventQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

internal abstract class LegacyInvokerDelegate(private val useReadAction: ThreeState) : InvokerDelegate {
  private val indicators = ConcurrentHashMap<AsyncPromise<*>, ProgressIndicatorBase>()

  override fun dispose() {
    while (!indicators.isEmpty()) {
      indicators.keys.forEach { it.cancel() }
    }
  }

  override fun run(task: Runnable, promise: AsyncPromise<*>): Boolean {
    if (ApplicationManager.getApplication() == null) {
      task.run() // is not interruptible in tests without application
    }
    else if (useReadAction != ThreeState.YES || EventQueue.isDispatchThread()) {
      ProgressManager.getInstance().runProcess(task, indicator(promise))
    }
    else if (!ProgressIndicatorUtils.runInReadActionWithWriteActionPriority(task, indicator(promise))) {
      return false
    }
    return true
  }

  private fun indicator(promise: AsyncPromise<*>): ProgressIndicatorBase {
    var indicator = indicators[promise]
    if (indicator == null) {
      indicator = ProgressIndicatorBase(true, false)
      val old = indicators.put(promise, indicator)
      if (old != null) Invoker.LOG.error("the same task is running in parallel")
      promise.onProcessed { indicators.remove(promise)?.cancel() }
    }
    return indicator
  }
}

internal class EdtLegacyInvokerDelegate(override val description: String) : LegacyInvokerDelegate(ThreeState.UNSURE) {
  override fun offer(runnable: Runnable, delay: Int, promise: Promise<*>) {
    if (delay > 0) {
      EdtExecutorService.getScheduledExecutorInstance().schedule(runnable, delay.toLong(), TimeUnit.MILLISECONDS)
    }
    else {
      EdtExecutorService.getInstance().execute(runnable)
    }
  }
}

internal class BgtLegacyInvokerDelegate(
  override val description: String,
  useReadAction: Boolean,
  maxThreads: Int,
) : LegacyInvokerDelegate(if (useReadAction) ThreeState.YES else ThreeState.NO) {
  private val executor = AppExecutorUtil.createBoundedScheduledExecutorService(description, maxThreads)

  override fun dispose() {
    super.dispose()
    executor.shutdown()
  }

  override fun offer(runnable: Runnable, delay: Int, promise: Promise<*>) {
    if (delay > 0) {
      executor.schedule(runnable, delay.toLong(), TimeUnit.MILLISECONDS);
    }
    else {
      executor.execute(runnable);
    }
  }
}
