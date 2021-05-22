// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("EXPERIMENTAL_API_USAGE", "EXPERIMENTAL_UNSIGNED_LITERALS")

package com.intellij.execution.process.mediator.daemon

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.takeWhile
import java.io.Closeable
import java.util.concurrent.atomic.AtomicReference


interface QuotaManager : Closeable {
  fun check(): Boolean

  /**
   * The returned job completes as soon as the quota is exceeded and all [runIfPermitted] blocks finished,
   * but never before [check] starts returning false.
   */
  fun asJob(): Job
}

/**
 * Check if the quota has not been exceeded yet,
 * and prevents the manager from completing its [job][QuotaManager.asJob] until the block finishes.
 */
@Throws(QuotaExceededException::class)
internal inline fun <R : Any> QuotaManager.runIfPermitted(block: () -> R): R {
  val childJob = Job(this.asJob())  // prevents the QuotaManager Job from finishing in case quota exceeds while running the block
  try {
    if (!this.check()) {
      throw QuotaExceededException()
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
      stopwatchRef.set(QuotaStopwatch.Expired)
      mutableStateFlow.value = null
    }
  }
  private val timeoutScope = coroutineScope + Job(job).also {
    if (job.complete()) {
      // doesn't in fact complete until the timeoutJob completes
      job.ensureActive()
    }
  }

  private val mutableStateFlow: MutableStateFlow<QuotaState?> = MutableStateFlow(stopwatchRef.get().toState())

  val stateUpdateFlow: Flow<QuotaState>
    get() = mutableStateFlow.takeWhile { it != null }.filterNotNull()

  override fun asJob(): Job = job

  override fun check(): Boolean {
    val stopwatch = updateStopwatch { it.refresh() }
    return stopwatch !is QuotaStopwatch.Expired
  }

  fun adjustQuota(newOptions: QuotaOptions) {
    updateStopwatch { it.adjust(newOptions) }
  }

  private fun updateStopwatch(function: (t: QuotaStopwatch) -> QuotaStopwatch): QuotaStopwatch {
    return stopwatchRef.updateAndGet(function).also { stopwatch ->
      onStopwatchUpdated(stopwatch)
    }
  }

  private fun updateStopwatch(expectedStopwatch: QuotaStopwatch, stopwatch: QuotaStopwatch): Boolean {
    return stopwatchRef.compareAndSet(expectedStopwatch, stopwatch).also { success ->
      if (success) {
        onStopwatchUpdated(stopwatch)
      }
    }
  }

  private fun onStopwatchUpdated(stopwatch: QuotaStopwatch) {
    when (stopwatch) {
      is QuotaStopwatch.Active -> {
        if (!stopwatch.isUnlimited) {
          timeoutScope.launch(start = CoroutineStart.UNDISPATCHED) {
            delay(stopwatch.remaining())
            updateStopwatch(stopwatch, QuotaStopwatch.Expired)
          }
        }
      }
      QuotaStopwatch.Expired -> {
        timeoutScope.cancel("expired")
      }
    }
    mutableStateFlow.value = stopwatch.toState()
  }

  override fun close() {
    job.cancel("Closed")
  }
}


private sealed class QuotaStopwatch {
  abstract val options: QuotaOptions

  /**
   * This can only reduce the quota. An already exceeded quota doesn't change.
   */
  abstract fun adjust(newOptions: QuotaOptions): QuotaStopwatch

  /**
   * Starts the stopwatch if it hasn't started yet.
   * This may reset the active stopwatch, if the quota allows to do so via [QuotaOptions.isRefreshable].
   */
  abstract fun refresh(): QuotaStopwatch

  abstract fun toState(): QuotaState

  data class New(
    override val options: QuotaOptions,
  ) : QuotaStopwatch() {
    override fun adjust(newOptions: QuotaOptions): QuotaStopwatch = copy(options = options.adjust(newOptions))
    override fun refresh(): QuotaStopwatch = Active(options)  // the very first process is always permitted
    override fun toState() = QuotaState.New(options)
  }

  data class Active(
    override val options: QuotaOptions,
    private val startTimeMillis: Long = System.currentTimeMillis(),
  ) : QuotaStopwatch() {
    val isUnlimited get() = options.isUnlimited

    fun elapsed(): Long = if (isUnlimited) 0 else System.currentTimeMillis() - startTimeMillis
    fun remaining(): Long = if (isUnlimited) Long.MAX_VALUE else options.timeLimitMs - elapsed()

    private fun isExpired(): Boolean = remaining() <= 0

    override fun adjust(newOptions: QuotaOptions): QuotaStopwatch = when {
      isExpired() -> Expired
      else -> copy(options = options.adjust(newOptions))
    }

    override fun refresh(): QuotaStopwatch = when {
      isExpired() -> Expired
      !options.isRefreshable -> this
      else -> copy(startTimeMillis = System.currentTimeMillis())
    }

    override fun toState() = QuotaState.Active(options, elapsed())
  }

  object Expired : QuotaStopwatch() {
    override val options: QuotaOptions
      get() = QuotaOptions.EXCEEDED

    override fun adjust(newOptions: QuotaOptions): QuotaStopwatch = this
    override fun refresh(): QuotaStopwatch = this
    override fun toState() = QuotaState.Expired
  }
}
