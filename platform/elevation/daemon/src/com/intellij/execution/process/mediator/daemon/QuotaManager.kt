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


class TimeQuotaManager(
  coroutineScope: CoroutineScope,
  quota: TimeQuota = TimeQuota(QuotaOptions.UNLIMITED),
) : QuotaManager, CoroutineScope by coroutineScope {
  private val quotaRef = AtomicReference(quota)

  private val job = Job(coroutineContext[Job])
  private val timeoutActor = createTimeoutActor(job)

  init {
    job.invokeOnCompletion {
      quotaRef.set(TimeQuota.EXCEEDED)
    }
    if (job.complete()) {
      // doesn't in fact complete until the child actor completes
      job.ensureActive()
    }
    timeoutActor.offer(quota)
  }

  override fun asJob(): Job = job

  override fun check(): Boolean {
    val quota = updateQuota { it.refresh() }
    return !quota.isExceeded()
  }

  fun adjustQuota(newOptions: QuotaOptions): TimeQuota {
    return updateQuota { it.adjust(newOptions) }
  }

  private fun updateQuota(function: (t: TimeQuota) -> TimeQuota): TimeQuota {
    return quotaRef.updateAndGet(function).takeIf { quota ->
      timeoutActor.tryOffer(quota)
    } ?: TimeQuota.EXCEEDED.also {
      quotaRef.set(it)
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
    private fun CoroutineScope.createTimeoutActor(context: CoroutineContext): SendChannel<TimeQuota> {
      return actor(context, capacity = Channel.CONFLATED) {
        var quota: TimeQuota = channel.receive()
        while (true) {
          quota = select {
            channel.onReceive { it }

            if (!quota.isUnlimited) {
              onTimeout(quota.remaining()) { null }
            }
          } ?: break
        }
        channel.close()  // otherwise the channel becomes cancelled once the actor returns
      }
    }
  }
}
