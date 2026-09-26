// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.navigation.impl

import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Helper logic to separate states of "Prepare" and "Compute" steps.
 * Similar to [com.intellij.platform.util.coroutines.sync.OverflowSemaphore] but acts in 2 phases.
 *
 * A task enters the race only once its `prepare` produced something to apply: one which prepared nothing is no-op.
 * Among the tasks which do have something to apply, the one submitted last wins.
 * If task took a turn and then failed to apply anything, turn is given to the one of older on the state of `prepare`.
 * A task which lost the turn stops at once, so a slow apply does not collect a queue of tasks which cannot win.
 *
 * A preparation can register its target with [Preparation.addKey].
 * The newest submission for the same target cancels the older one immediately, even if that one already waits for its turn.
 *
 * NB: used as a temporary step before clean async editor open separation, so that there is only `one` critical section
 * on `EDT`.
 */
@ApiStatus.Internal
class TwoPhaseOverflowExecutor {
  private val applyMutex: Mutex = Mutex()

  private val lastTaskId: AtomicInteger = AtomicInteger()
  /**
   * The task which is currently inside its "apply" phase, waits for it or has already left it.
   * Only the task which holds the turn is allowed to apply.
   */
  private val activeTask: AtomicReference<RunningTask> = AtomicReference(NO_TASK)

  /**
   * Tasks which are still at "prepare" phase.
   */
  private val preparingTasks: ConcurrentMap<Int, Preparation> = ConcurrentHashMap()

  /**
   * Target keys owned by running preparations.
   */
  private val keyOwners = ArrayList<KeyOwner>()

  /**
   * Runs [prepare] concurrently with the currently applied task, then applies its result as the latest task.
   * Claim the target key with [Preparation.addKey] as the preparation resolves it.
   *
   * @return the result of [action], or `null` if either phase produces nothing or a newer submission holds the turn.
   * A preparation which stops because a newer submission owns its key also returns `null`.
   * A submission which applies nothing preserves preparations for other targets.
   */
  suspend fun <T : Any, R : Any> submit(prepare: suspend Preparation.() -> T?, action: suspend (T) -> R?): R? = coroutineScope {
    val id = lastTaskId.incrementAndGet()
    val preparation = Preparation(id, currentCoroutineContext().job)
    try {
      val prepared = try {
        preparingTasks[id] = preparation
        preparation.prepare()
      }
      finally {
        preparingTasks.remove(id)
      }

      ensureActive()
      if (prepared == null) {
        return@coroutineScope null
      }
      val turn = RunningTask(id, currentCoroutineContext().job)
      val replaced = tryClaimTurnIfNewest(turn) ?: return@coroutineScope null
      // the replaced task cannot win anymore, and it must not wait behind a slow 'apply' phase
      replaced.job.cancel("Superseded by a newer submission")

      var applied = false
      try {
        val result = doExclusively(turn, prepared, action)
        applied = result != null
        result
      }
      finally {
        if (applied) {
          dropOlderPreparations(id)
        }
        else {
          // the turn was taken but nothing was applied: give it back, so that an older submission can still win with its own result
          activeTask.compareAndSet(turn, replaced)
        }
      }
    }
    finally {
      preparation.releaseKeys()
    }
  }

  /**
   * @return the task which was holding the turn before, or `null` if a newer submission already holds it
   */
  private fun tryClaimTurnIfNewest(turn: RunningTask): RunningTask? {
    while (true) {
      val holder = activeTask.get()
      if (holder.id > turn.id) {
        return null
      }
      if (activeTask.compareAndSet(holder, turn)) {
        return holder
      }
    }
  }

  private fun dropOlderPreparations(id: Int) {
    for ((preparingId, task) in preparingTasks) {
      if (preparingId < id) {
        task.job.cancel("Superseded by a newer submission")
      }
    }
  }

  private suspend fun <T : Any, R : Any> doExclusively(turn: RunningTask, prepared: T, action: suspend (T) -> R?): R? {
    if (activeTask.get().id != turn.id) {
      // a later submission got something to apply, so there is no reason to wait for the lock
      return null
    }
    return applyMutex.withLock {
      if (activeTask.get().id != turn.id) {
        // a later submission got something to apply while this one was waiting for the lock
        return@withLock null
      }
      action(prepared)
    }
  }

  private class RunningTask(@JvmField val id: Int, @JvmField val job: Job)
  private class KeyOwner(val key: Any, val preparation: Preparation)

  private companion object {
    /**
     * Turn which nobody holds: older than any submission, and it has nothing to cancel.
     */
    private val NO_TASK: RunningTask = RunningTask(id = 0, job = NonCancellable)
  }

  /**
   * Each task's key remains claimed until the whole submission finishes, so a newer submission of the same target
   * also cancels one which already waits for its turn.
   */
  inner class Preparation internal constructor(private val id: Int, internal val job: Job) {

    /**
     * Puts [key] as the target of this preparation.
     * If successful, cancels the older preparation of the same target.
     * Repeated claims by this preparation succeed without cancellation.
     *
     * @return `false` when a newer submission owns the key. The caller must stop preparing in this case.
     */
    fun addKey(key: Any): Boolean {
      job.ensureActive()
      val previous = synchronized(keyOwners) {
        val index = keyOwners.indexOfFirst { it.key == key }
        if (index < 0) {
          keyOwners.add(KeyOwner(key, this))
          return true
        }
        val owner = keyOwners[index].preparation
        if (owner === this) return true
        if (owner.id > id) return false
        keyOwners[index] = KeyOwner(key, this)
        owner
      }
      previous.job.cancel("Superseded by a newer preparation of the same target")
      return true
    }

    internal fun releaseKeys() {
      synchronized(keyOwners) {
        keyOwners.removeAll { it.preparation === this }
      }
    }
  }
}
