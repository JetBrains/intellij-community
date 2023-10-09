// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.observable

import com.intellij.concurrency.currentThreadContext
import com.intellij.concurrency.installThreadContext
import com.intellij.util.concurrency.BlockingJob
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Allows to track subsystem activities and get a "dumb mode" w.r.t. tracked computations.
 *
 * To use this class, consider creating a service with an injected [CoroutineScope] and pass this scope here.
 * This is needed because the lifetime of tracker is bound by the lifetime of 'plugin x project' that use tracking.
 */
abstract class AbstractInProgressService(private val scope: CoroutineScope) {

  /**
   * Basically an imitation of [java.util.concurrent.Phaser], but in suspending way.
   */
  private val currentConfigurationGeneration : AtomicReference<CompletableJob> = AtomicReference(Job())

  private val concurrentConfigurationCounter = AtomicInteger(0)

  /**
   * Installs a tracker for a suspending asynchronous activity of [action].
   * This method is cheap to use: it does not perform any complex computations, and it is essentially equivalent to `withContext`.
   */
  suspend fun <T> trackConfigurationActivity(action: suspend () -> T) : T {
    return withBlockingJob { blockingJob ->
      withContext(blockingJob) {
        action()
      }
    }
  }

  /**
   * Installs a tracker for a blocking asynchronous activity of [action].
   * This method is cheap to use: it does not add any synchronization or complex computations.
   */
  @RequiresBlockingContext
  fun <T> trackConfigurationActivityBlocking(action: () -> T) : T {
    val currentContext = currentThreadContext()
    return withBlockingJob { blockingJob ->
      installThreadContext(currentContext + blockingJob, true).use {
        action()
      }
    }
  }

  private inline fun <T> withBlockingJob(consumer: (BlockingJob) -> T) : T {
    enterConfiguration()
    // new job here to track those and only those computations which are invoked under explicit `trackConfigurationActivity`
    val tracker = Job()
    tracker.invokeOnCompletion {
      leaveConfiguration()
    }
    val blockingJob = BlockingJob(tracker)
    try {
      return consumer(blockingJob)
    } finally {
      scope.launch {
        tracker.children.forEach { it.join() }
      }.invokeOnCompletion {
        tracker.complete()
      }
    }
  }

  private fun enterConfiguration() {
    concurrentConfigurationCounter.getAndIncrement()
  }

  private fun leaveConfiguration() {
    if (concurrentConfigurationCounter.decrementAndGet() == 0) {
      val currentJob = currentConfigurationGeneration.getAndSet(Job())
      currentJob.complete()
    }
  }

  open fun isInProgress(): Boolean {
    return concurrentConfigurationCounter.get() != 0
  }

  open suspend fun awaitConfiguration() {
    // order of lines is important
    val currentJob = currentConfigurationGeneration.get()
    if (isInProgress()) {
      // isInProgress == true -> either currentJob corresponds to the current configuration process,
      // or its configuration was completed earlier, and in this case join() will immediately return
      // isInProgress == false -> immediately return, since no configuration process is here currently
      currentJob.join()
    }
  }
}

