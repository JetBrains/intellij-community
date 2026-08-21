// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.LongAdder

class IjentStatCounter {

  private val methods = ConcurrentHashMap<String, IjentMethodStatAcc>()

  fun started(method: String, nanoTimeStart: Long) {
    methods.computeIfAbsent(method) { IjentMethodStatAcc() }.onStarted(nanoTimeStart)
  }

  fun finished(method: String, nanoTimeStart: Long, status: Int, nanoTimeFinish: Long) {
    methods.computeIfAbsent(method) { IjentMethodStatAcc() }.onFinished(nanoTimeStart, nanoTimeFinish)
  }

  // All the snapshot is weakly consistent because all values are updated atomically.
  fun snapshot(): Map<String, IjentMethodStat> {
    return methods.mapValues { (_, value) ->
      val finishedBefore = value.totalCallsFinished.sum()
      val startedBefore = value.totalCallsStarted.sum()
      val activeStarts = value.notFinishedOperationsStartNanos.snapshot()
      val finishedAfter = value.totalCallsFinished.sum()
      val startedAfter = value.totalCallsStarted.sum()

      val countersStable =
        finishedBefore == finishedAfter && startedBefore == startedAfter
      val pendingCount =
        (startedAfter - finishedBefore).coerceAtLeast(0)

      IjentMethodStat(
        totalCallsFinished = finishedAfter,
        totalNanos = value.totalNanos.sum(),
        lastOperationDurationNanos =
          if (finishedAfter > 0) value.lastOperationDurationNanos.getAcquire() else null,
        lastOperationFinishedNanos =
          if (finishedAfter > 0) value.lastOperationFinishedNanos.getAcquire() else null,
        pendingCalls = PendingCalls(
          count = pendingCount,
          oldestStartNanos = activeStarts.minOrNull(),
          oldestStartIsExact =
            countersStable && pendingCount == activeStarts.size.toLong(),
        ),
      )
    }
  }
}

internal class IjentMethodStatAcc {
  val totalCallsStarted = LongAdder()
  val totalCallsFinished = LongAdder()
  val totalNanos = LongAdder()
  val lastOperationDurationNanos = AtomicLong(-1L)
  val lastOperationFinishedNanos = AtomicLong(-1L)

  // if more than 4 grpc operations simultaneous, we can lose information about the duration of the oldest one
  val notFinishedOperationsStartNanos = StripedList(4)

  fun onStarted(nanoTimeStart: Long) {
    totalCallsStarted.increment()
    notFinishedOperationsStartNanos.add(nanoTimeStart)
  }

  fun onFinished(nanoTimeStart: Long, nanoTimeFinish: Long) {
    val duration = nanoTimeFinish - nanoTimeStart
    notFinishedOperationsStartNanos.remove(nanoTimeStart)
    totalNanos.add(duration)
    lastOperationDurationNanos.setRelease(duration)
    lastOperationFinishedNanos.setRelease(nanoTimeFinish)
    totalCallsFinished.increment()
  }
}

data class PendingCalls(
  val count: Long,
  val oldestStartNanos: Long?,
  val oldestStartIsExact: Boolean,
)

data class IjentMethodStat(
  val totalCallsFinished: Long,
  val totalNanos: Long,
  val lastOperationDurationNanos: Long?,
  val lastOperationFinishedNanos: Long?,
  val pendingCalls: PendingCalls,
)