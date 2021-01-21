// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("EXPERIMENTAL_API_USAGE", "EXPERIMENTAL_UNSIGNED_LITERALS")

package com.intellij.execution.process.mediator.daemon

import kotlinx.coroutines.*
import java.io.Closeable
import java.util.concurrent.atomic.AtomicReference


interface QuotaManager : Closeable {
  fun check(): Boolean

  /** The returned job completes as soon as the quota is exceeded, but never before [check] starts returning false. */
  fun asJob(): Job
}


internal suspend fun <R : Any> QuotaManager.runIfPermitted(block: suspend () -> R): R? {
  val childJob = Job(this.asJob())  // prevents the QuotaManager Job from finishing in case quota exceeds while running the block
  try {
    if (!this.check()) {
      return null
    }
    check(childJob.isActive) { "check() returned true after asJob() has completed" }

    return block()
  }
  finally {
    childJob.complete()
  }
}


internal class TimeQuotaManager(
  coroutineScope: CoroutineScope,
  quotaOptions: QuotaOptions = QuotaOptions.UNLIMITED,
) : QuotaManager {
  private val stopwatchRef: AtomicReference<QuotaStopwatch> = AtomicReference(QuotaStopwatch.New(quotaOptions))

  private val job = Job(coroutineScope.coroutineContext[Job]).apply {
    invokeOnCompletion {
      stopwatchRef.set(QuotaStopwatch.Exceeded)
    }
  }
  private val timeoutJob = Job(job).also {
    if (job.complete()) {
      // doesn't in fact complete until the timeoutJob completes
      job.ensureActive()
    }
  }
  private val timeoutScope = coroutineScope + timeoutJob

  override fun asJob(): Job = job

  override fun check(): Boolean {
    val quota = updateStopwatch { it.refresh() }
    return quota !is QuotaStopwatch.Exceeded
  }

  fun adjustQuota(newOptions: QuotaOptions) {
    updateStopwatch { it.adjust(newOptions) }
  }

  private fun updateStopwatch(function: (t: QuotaStopwatch) -> QuotaStopwatch): QuotaStopwatch {
    return stopwatchRef.updateAndGet(function).also { stopwatch ->
      if (stopwatch == QuotaStopwatch.Exceeded) {
        timeoutJob.cancel("expired")
      }
      if (stopwatch is QuotaStopwatch.Active && !stopwatch.isUnlimited) {
        timeoutScope.launch(start = CoroutineStart.UNDISPATCHED) {
          delay(stopwatch.remaining())
          if (stopwatchRef.compareAndSet(stopwatch, QuotaStopwatch.Exceeded)) {
            timeoutJob.cancel("expired")
          }
        }
      }
    }
  }

  override fun close() {
    job.cancel("Closed")
  }
}


private sealed class QuotaStopwatch {
  /**
   * This can only reduce the quota. An already exceeded quota doesn't change.
   */
  abstract fun adjust(newOptions: QuotaOptions): QuotaStopwatch

  /**
   * Starts the stopwatch if it hasn't started yet.
   * This may reset the active stopwatch, if the quota allows to do so via [QuotaOptions.isRefreshable].
   */
  abstract fun refresh(): QuotaStopwatch


  data class New(
    val options: QuotaOptions,
  ) : QuotaStopwatch() {
    override fun adjust(newOptions: QuotaOptions): QuotaStopwatch = copy(options = options.adjust(newOptions))
    override fun refresh(): QuotaStopwatch = Active(options)  // the very first process is always permitted
  }

  data class Active(
    val options: QuotaOptions,
    private val startTimeMillis: Long = System.currentTimeMillis(),
  ) : QuotaStopwatch() {
    val isUnlimited get() = options.isUnlimited

    fun elapsed(): Long = if (isUnlimited) 0 else System.currentTimeMillis() - startTimeMillis
    fun remaining(): Long = if (isUnlimited) Long.MAX_VALUE else options.timeLimitMs - elapsed()

    private fun isExceeded(): Boolean = remaining() <= 0

    override fun adjust(newOptions: QuotaOptions): QuotaStopwatch = when {
      isExceeded() -> Exceeded
      else -> copy(options = options.adjust(newOptions))
    }

    override fun refresh(): QuotaStopwatch = when {
      isExceeded() -> Exceeded
      !options.isRefreshable -> this
      else -> copy(startTimeMillis = System.currentTimeMillis())
    }
  }

  object Exceeded : QuotaStopwatch() {
    override fun adjust(newOptions: QuotaOptions): QuotaStopwatch = this
    override fun refresh(): QuotaStopwatch = this
  }
}
