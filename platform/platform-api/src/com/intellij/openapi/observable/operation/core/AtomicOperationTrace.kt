// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.observable.operation.core

import com.intellij.openapi.observable.operation.OperationExecutionId
import com.intellij.openapi.observable.operation.OperationExecutionStatus
import org.jetbrains.annotations.ApiStatus
import com.intellij.openapi.observable.operation.core.ObservableOperationStatus.*
import java.util.concurrent.atomic.AtomicReference
import kotlin.collections.plus
import kotlin.collections.minus

@ApiStatus.Experimental
class AtomicOperationTrace(
  override val name: String,
  private val isMerging: Boolean
) : AbstractObservableOperationTrace(),
    MutableOperationTrace {

  constructor() : this("UNKNOWN", false)
  constructor(name: String) : this(name, false)
  constructor(isMerging: Boolean) : this("UNKNOWN", isMerging)

  private val state = AtomicReference(State(COMPLETED, mapOf(), mapOf()))

  override val status get() = state.get().status

  override fun traceSchedule(id: OperationExecutionId) {
    val (old, new) = updateState {
      State(
        status = when (it.status) {
          SCHEDULED -> SCHEDULED
          IN_PROGRESS -> IN_PROGRESS
          COMPLETED -> SCHEDULED
        },
        scheduled = it.scheduled.update(id, +1),
        started = it.started
      )
    }
    forEachTask(new.scheduled.minus(old.scheduled)) {
      fireTaskScheduled(it)
    }
  }

  override fun traceStart(id: OperationExecutionId) {
    val (old, new) = updateState {
      val numStarted = if (isMerging) maxOf(1, it.scheduled.count(id)) else 1
      State(
        status = IN_PROGRESS,
        scheduled = it.scheduled.update(id, -numStarted),
        started = it.started.update(id, +numStarted)
      )
    }
    forEachTask(new.started.minus(old.started)) {
      fireTaskStarted(it)
    }
  }

  override fun traceFinish(id: OperationExecutionId, status: OperationExecutionStatus) {
    val (old, new) = updateState {
      val numFinished = if (isMerging) it.started.count(id) else 1
      val started = it.started.update(id, -numFinished)
      State(
        status = when {
          started.count() > 0 -> IN_PROGRESS
          it.scheduled.count() > 0 -> IN_PROGRESS
          else -> COMPLETED
        },
        scheduled = it.scheduled,
        started = started
      )
    }
    forEachTask(old.started.minus(new.started)) {
      fireTaskFinished(it, status)
    }
  }

  override fun detach(id: OperationExecutionId) {
    val (old, new) = updateState {
      val scheduled = it.scheduled.remove(id)
      val started = it.started.remove(id)
      State(
        status = when {
          started.count() > 0 -> IN_PROGRESS
          scheduled.count() > 0 -> it.status
          else -> COMPLETED
        },
        scheduled = scheduled,
        started = started
      )
    }
    forEachTask(old.scheduled.minus(new.scheduled)) {
      fireTaskDetached(it)
    }
    forEachTask(old.started.minus(new.started)) {
      fireTaskDetached(it)
    }
  }

  override fun detachAll() {
    val (old, new) = updateState {
      State(
        status = COMPLETED,
        scheduled = emptyMap(),
        started = emptyMap()
      )
    }
    forEachTask(old.scheduled.minus(new.scheduled)) {
      fireTaskDetached(it)
    }
    forEachTask(old.started.minus(new.started)) {
      fireTaskDetached(it)
    }
  }

  private fun updateState(nextState: (State) -> State): Pair<State, State> {
    lateinit var oldState: State
    val state = state.updateAndGet {
      oldState = it
      nextState(it)
    }
    if (oldState.status != state.status) {
      when (state.status) {
        SCHEDULED -> fireOperationScheduled()
        IN_PROGRESS -> fireOperationStarted()
        COMPLETED -> fireOperationFinished()
      }
    }
    return oldState to state
  }

  private fun forEachTask(
    state: Map<OperationExecutionId, Int>,
    action: (OperationExecutionId) -> Unit
  ) {
    for ((id, count) in state) {
      repeat(count) {
        action(id)
      }
    }
  }

  override fun toString(): String {
    val (status, scheduled, started) = state.get()
    return "$name: " +
           "$status " +
           "scheduled=${scheduled.asString()} " +
           "started=${started.asString()}"
  }

  private fun Map<OperationExecutionId, Int>.asString(): String {
    return entries.groupBy({ it.key.toString() }, { it.value })
      .mapValues { it.value.sum() }
      .toString()
  }

  @ApiStatus.Internal
  fun getExecutions(): Set<OperationExecutionId> {
    val (_, scheduled, started) = state.get()
    return scheduled.keys + started.keys
  }

  private data class State(
    val status: ObservableOperationStatus,
    val scheduled: Map<OperationExecutionId, Int>,
    val started: Map<OperationExecutionId, Int>
  )

  companion object {

    private fun <K> Map<K, Int>.count(): Int {
      return entries.fold(0) { acc, it -> acc + it.value }
    }

    private fun <K> Map<K, Int>.count(id: K): Int {
      return get(id) ?: 0
    }

    private fun <K> Map<K, Int>.update(id: K, value: Int): Map<K, Int> {
      val numTasks = count(id) + value
      if (numTasks > 0) {
        return this + (id to numTasks)
      }
      return remove(id)
    }

    private fun <K> Map<K, Int>.minus(map: Map<K, Int>): Map<K, Int> {
      return mapValues { it.value - map.count(it.key) }
        .filter { it.value > 0 }
    }

    private fun <K> Map<K, Int>.remove(id: K): Map<K, Int> {
      return this - id
    }
  }
}