// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.observation

import com.intellij.concurrency.currentThreadContext
import com.intellij.concurrency.installThreadContext
import com.intellij.util.concurrency.BlockingJob
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Allows to track subsystem activities and get a "dumb mode" w.r.t. tracked computations.
 *
 * To use this class, consider creating a service with an injected [CoroutineScope] and pass this scope here.
 * This is needed because the lifetime of tracker is bound by the lifetime of 'plugin ∩ project' that use tracking.
 */
abstract class AbstractInProgressService(private val scope: CoroutineScope) {

  @Volatile
  private var inProgress: Int = 0

  suspend fun <T> trackConfigurationActivity(action: suspend () -> T) : T {
    return withBlockingJob { blockingJob ->
      withContext(blockingJob) {
        action()
      }
    }
  }

  @RequiresBlockingContext
  fun <T> trackConfigurationActivityBlocking(action: () -> T) : T {
    return withBlockingJob { blockingJob ->
      val currentContext = currentThreadContext()
      installThreadContext(currentContext + blockingJob, true).use {
        action()
      }
    }
  }

  private inline fun <T> withBlockingJob(consumer: (BlockingJob) -> T) : T {
    inProgress += 1
    val tracker = Job()
    val blockingJob = BlockingJob(tracker)
    tracker.invokeOnCompletion {
      inProgress -= 1
    }
    try {
      return consumer(blockingJob)
    } finally {
      scope.launch {
        tracker.children.forEach { it.join() }
        tracker.complete()
      }
    }
  }

  fun isInProgress(): Boolean {
    return inProgress != 0
  }
}

