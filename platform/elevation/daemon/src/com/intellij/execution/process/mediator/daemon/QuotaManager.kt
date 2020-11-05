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
) : QuotaManager, CoroutineScope by coroutineScope {
  private val stopwatchRef: AtomicReference<QuotaStopwatch>

  private val job = Job(coroutineContext[Job])
  private val timeoutActor = createTimeoutActor(job)

  init {
    val stopwatch = QuotaStopwatch(quotaOptions)
    stopwatchRef = AtomicReference(stopwatch)

    job.invokeOnCompletion {
      stopwatchRef.set(QuotaStopwatch.EXCEEDED)
    }
    if (job.complete()) {
      // doesn't in fact complete until the child actor completes
      job.ensureActive()
    }
    timeoutActor.offer(stopwatch)
  }

  override fun asJob(): Job = job

  override fun check(): Boolean {
    val quota = updateQuota { it.refresh() }
    return !quota.isExceeded()
  }

  fun adjustQuota(newOptions: QuotaOptions) {
    updateQuota { it.adjust(newOptions) }
  }

  private fun updateQuota(function: (t: QuotaStopwatch) -> QuotaStopwatch): QuotaStopwatch {
    return stopwatchRef.updateAndGet(function).takeIf { quota ->
      timeoutActor.tryOffer(quota)
    } ?: QuotaStopwatch.EXCEEDED.also {
      stopwatchRef.set(it)
    }
  }

  override fun close() {
    cancel("Closed")
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

  companion object {
    private fun CoroutineScope.createTimeoutActor(context: CoroutineContext): SendChannel<QuotaStopwatch> {
      return actor(context, capacity = Channel.CONFLATED) {
        var quotaStopwatch: QuotaStopwatch = channel.receive()
        while (true) {
          quotaStopwatch = select {
            channel.onReceive { it }

            if (!quotaStopwatch.isUnlimited) {
              onTimeout(quotaStopwatch.remaining()) { null }
            }
          } ?: break
        }
        channel.close()  // otherwise the channel becomes cancelled once the actor returns
      }
    }
  }
}


private data class QuotaStopwatch(
  val options: QuotaOptions,
  private val startTimeMillis: Long = if (options == QuotaOptions.EXCEEDED) 0 else System.currentTimeMillis(),
) {
  val isUnlimited get() = options.isUnlimited

  fun elapsed(): Long = if (isUnlimited) 0 else System.currentTimeMillis() - startTimeMillis
  fun remaining(): Long = if (isUnlimited) Long.MAX_VALUE else options.timeLimitMs - elapsed()

  fun isExceeded(): Boolean = remaining() <= 0

  /**
   * This can only reduce the quota. An already exceeded quota doesn't change.
   * The [startTimeMillis] is not altered, use [refresh] instead.
   */
  fun adjust(newOptions: QuotaOptions): QuotaStopwatch =
    if (isExceeded()) this
    else copy(options = options.adjust(newOptions))

  fun refresh(): QuotaStopwatch =
    if (!options.isRefreshable || isExceeded()) this else copy(startTimeMillis = System.currentTimeMillis())

  companion object {
    val EXCEEDED = QuotaStopwatch(QuotaOptions.EXCEEDED)
  }
}
