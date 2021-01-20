// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("EXPERIMENTAL_API_USAGE", "EXPERIMENTAL_UNSIGNED_LITERALS")

package com.intellij.execution.process.mediator.daemon

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.selects.select
import java.io.Closeable
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.CoroutineContext


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
  private val stopwatchRef: AtomicReference<QuotaStopwatch>

  private val job = Job(coroutineScope.coroutineContext[Job])
  private val timeoutActor = coroutineScope.createTimeoutActor(job)

  init {
    val stopwatch = QuotaStopwatch.New(quotaOptions)
    stopwatchRef = AtomicReference(stopwatch)

    job.invokeOnCompletion {
      stopwatchRef.set(QuotaStopwatch.Exceeded)
    }
    if (job.complete()) {
      // doesn't in fact complete until the child actor completes
      job.ensureActive()
    }
    timeoutActor.offer(stopwatch)
  }

  override fun asJob(): Job = job

  override fun check(): Boolean {
    val quota = updateStopwatch { it.refresh() }
    return quota !is QuotaStopwatch.Exceeded
  }

  fun adjustQuota(newOptions: QuotaOptions) {
    updateStopwatch { it.adjust(newOptions) }
  }

  private fun updateStopwatch(function: (t: QuotaStopwatch) -> QuotaStopwatch): QuotaStopwatch {
    return stopwatchRef.updateAndGet(function).takeIf { stopwatch ->
      timeoutActor.tryOffer(stopwatch)
    } ?: QuotaStopwatch.Exceeded.also {
      stopwatchRef.set(it)
    }
  }

  override fun close() {
    job.cancel("Closed")
  }
}

private fun <T> SendChannel<T>.tryOffer(quota: T): Boolean {
  return try {
    offer(quota)
  }
  catch (e: CancellationException) {
    false
  }
  catch (e: ClosedSendChannelException) {
    false
  }
}

private fun CoroutineScope.createTimeoutActor(context: CoroutineContext): SendChannel<QuotaStopwatch> {
  return actor(context, capacity = Channel.CONFLATED) {
    var stopwatch: QuotaStopwatch = channel.receive()

    while (stopwatch !is QuotaStopwatch.Exceeded) {
      stopwatch = select {
        channel.onReceive { it }

        (stopwatch as? QuotaStopwatch.Active)?.let { activeStopwatch ->
          if (!activeStopwatch.isUnlimited) {
            onTimeout(activeStopwatch.remaining()) { QuotaStopwatch.Exceeded }
          }
        }
      }
    }
    channel.close()  // otherwise the channel becomes cancelled once the actor returns
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
