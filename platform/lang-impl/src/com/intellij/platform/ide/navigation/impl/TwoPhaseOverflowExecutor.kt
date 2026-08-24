// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.navigation.impl

import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Helper logic to separate states of "Prepare" and "Compute" steps.
 * Similar to [com.intellij.platform.util.coroutines.sync.OverflowSemaphore] but acts in 2 phases.
 *
 * A task enters the race only once its `prepare` produced something to apply: one which prepared nothing is no-op.
 * Among the tasks which do have something to apply, the one submitted last wins.
 * If task took a turn and then failed to apply anything, turn is given to the one of older on the state of `prepare`.
 *
 * NB: used as a temporary step before clean async editor open separation, so that there is only `one` critical section
 * on `EDT`.
 */
internal class TwoPhaseOverflowExecutor {
  private val applyMutex: Mutex = Mutex()

  private val lastTaskId: AtomicInteger = AtomicInteger()
  /**
   * The task which is currently inside its "apply" phase
   */
  @Volatile
  private var activeTask: RunningTask? = null

  /**
   * Tasks which are still at "prepare" phase.
   * A task which missed the cancellation loses the race later on [latestPreparedId] anyway.
   */
  private val preparingTasks: ConcurrentMap<Int, Job> = ConcurrentHashMap()

  /**
   * The highest id which has something to apply. Only the submission holding it is allowed to apply.
   */
  private val latestPreparedId: AtomicInteger = AtomicInteger()

  /**
   * Runs [prepare] concurrently with the currently applied task, then applies its result as the latest task.
   * `null` in [prepare] means there is nothing to invoke at all, `null` in [action] means it applied nothing:
   * both give the turn back instead of taking it away from an older task.
   */
  suspend fun <T : Any, R : Any> submit(prepare: suspend () -> T?, action: suspend (T) -> R?): R? = coroutineScope {
    val id = lastTaskId.incrementAndGet()
    val prepared = try {
      preparingTasks[id] = currentCoroutineContext().job
      prepare()
    }
    finally {
      preparingTasks.remove(id)
    }

    if (prepared == null) {
      return@coroutineScope null
    }
    val supersededId = tryClaimTurnIfNewest(id) ?: return@coroutineScope null
    val applying = activeTask
    if (applying != null && applying.id < id) {
      applying.job.cancel("Superseded by a newer submission")
    }

    var applied = false
    try {
      val result = doExclusively(id, prepared, action)
      applied = result != null
      result
    }
    finally {
      if (applied) {
        dropOlderPreparations(id)
      }
      else {
        // the turn was taken but nothing was applied: give it back, so that an older submission can still win with its own result
        latestPreparedId.compareAndSet(id, supersededId)
      }
    }
  }

  /**
   * @return the id which was holding the turn before, or `null` if a newer submission already holds it
   */
  private fun tryClaimTurnIfNewest(id: Int): Int? {
    while (true) {
      val claimedId = latestPreparedId.get()
      if (claimedId > id) {
        return null
      }
      if (latestPreparedId.compareAndSet(claimedId, id)) {
        return claimedId
      }
    }
  }

  private fun dropOlderPreparations(id: Int) {
    for ((preparingId, job) in preparingTasks) {
      if (preparingId < id) {
        job.cancel("Superseded by a newer submission")
      }
    }
  }

  private suspend fun <T : Any, R : Any> doExclusively(id: Int, prepared: T, action: suspend (T) -> R?): R? {
    return applyMutex.withLock {
      if (latestPreparedId.get() != id) {
        // a later submission got something to apply while this one was waiting for the lock
        return@withLock null
      }
      activeTask = RunningTask(id, currentCoroutineContext().job)
      try {
        action(prepared)
      }
      finally {
        activeTask = null
      }
    }
  }

  private class RunningTask(@JvmField val id: Int, @JvmField val job: Job)
}
