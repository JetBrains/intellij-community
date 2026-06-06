// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.community.ui.actions.dashboard

import com.intellij.platform.ijent.IjentEvent
import com.intellij.platform.ijent.IjentEventBus
import com.intellij.platform.ijent.IjentEventBusListener
import com.intellij.platform.ijent.IjentRequestEvent
import com.intellij.platform.ijent.IjentResponseEvent
import com.intellij.platform.util.coroutines.flow.throttle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.transformLatest
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds

internal class IjentStatCounter {
  private val eventAggregator: EventAggregator<IjentEvent, MutableMap<String, IjentStatData>, Map<String, IjentStatData>> = EventAggregator(
    initialState = mutableMapOf(),
    aggregate = { acc, event ->
      acc[event.method] = (acc[event.method] ?: IjentStatData.EMPTY) + event
    },
    createSnapshot = { it.toMap() }
  )
  val listener: IjentEventBusListener = object : IjentEventBusListener {
    override fun onEvent(event: IjentEvent) {
      eventAggregator.onEvent(event)
    }
  }
  inline fun <T> process(eventBus: IjentEventBus, f: () -> T): T {
    eventBus.addListener(listener)
    return try {
      f()
    }
    finally {
      eventBus.removeListener(listener)
    }
  }
  internal fun snapshotFlow(tickDuration: Duration): Flow<Map<String, IjentStatData>> =
    eventAggregator.changes
      .onStart { emit(Unit) }
      .throttle(tickDuration.inWholeMilliseconds)
      .repeatLatestWhenIdle(tickDuration)
      .map {
        eventAggregator.getSnapshot()
      }
}

private class EventAggregator<E, M, S>(
  initialState: M,
  private val aggregate: (M, E) -> Unit,
  private val createSnapshot: (M) -> S
) {
  private val lock = Any()
  private val state: M = initialState

  private val updateSignal = MutableSharedFlow<Unit>(
    extraBufferCapacity = 1,
    onBufferOverflow = BufferOverflow.DROP_OLDEST
  )

  fun onEvent(event: E) {
    synchronized(lock) {
      aggregate(state, event)
    }
    updateSignal.tryEmit(Unit)
  }

  val changes: Flow<Unit> = updateSignal

  fun getSnapshot(): S = synchronized(lock) {
    createSnapshot(state)
  }
}

internal data class IjentStatData(
  val totalCallsStarted: Int,
  val totalCallsFinished: Int,
  val totalCallsDuration: Duration,
  val lastOperationDurationNanos: Long?,
  val lastOperationFinishedNanos: Long?,
  val notFinishedOperationsStartNanos: List<Long>,
) {
  operator fun plus(event: IjentEvent): IjentStatData {
    when (event) {
      is IjentRequestEvent -> {
        return IjentStatData(
          totalCallsStarted + 1,
          totalCallsFinished,
          totalCallsDuration,
          lastOperationDurationNanos,
          lastOperationFinishedNanos,
          notFinishedOperationsStartNanos + event.nanoTimeStart
        )
      }
      is IjentResponseEvent -> {
        return IjentStatData(
          totalCallsStarted,
          totalCallsFinished + 1,
          totalCallsDuration + (event.nanoTimeFinish - event.request.nanoTimeStart).nanoseconds,
          event.nanoTimeFinish - event.request.nanoTimeStart,
          event.nanoTimeFinish,
          notFinishedOperationsStartNanos.minus(event.request.nanoTimeStart)
        )
      }
    }
  }
  companion object {
    val EMPTY: IjentStatData = IjentStatData(0, 0, Duration.ZERO, null, null, emptyList())
  }
}

@OptIn(ExperimentalCoroutinesApi::class)
private fun <T> Flow<T>.repeatLatestWhenIdle(timeout: Duration): Flow<T> = transformLatest { value ->
  while (true) {
    emit(value)
    delay(timeout)
  }
}