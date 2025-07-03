// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.rpc

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.annotations.ApiStatus

private const val DEFAULT_RESULT_THROTTLING_MS: Long = 900
private const val DEFAULT_RESULT_COUNT_TO_STOP_THROTTLING: Int = 15

/**
 * Accumulate results until DEFAULT_RESULT_THROTTLING_MS has past or DEFAULT_RESULT_COUNT_TO_STOP_THROTTLING items where received,
 * then send the first batch of items inside ThrottledAccumulatedItems, and then send on demand one by one ThrottledOneItem.
 */
@ApiStatus.Internal
fun <T> Flow<T>.throttledWithAccumulation(resultThrottlingMs: Long = DEFAULT_RESULT_THROTTLING_MS,
                                          shouldPassItem: (T) -> Boolean = { true },
                                          resultCountToStopThrottling: Int = DEFAULT_RESULT_COUNT_TO_STOP_THROTTLING): Flow<ThrottledItems<T>> =
  throttledWithAccumulation(resultThrottlingMs, shouldPassItem) { _, accumulatedSize ->
    if (accumulatedSize >= resultCountToStopThrottling) 0 else null
  }

@OptIn(DelicateCoroutinesApi::class)
@ApiStatus.Internal
fun <T> Flow<T>.throttledWithAccumulation(resultThrottlingMs: Long = DEFAULT_RESULT_THROTTLING_MS,
                                          shouldPassItem: (T) -> Boolean,
                                          shouldStopThrottlingAfterDelay: (T, Int) -> Long?): Flow<ThrottledItems<T>> {
  val originalFlow = this
  return channelFlow {
    var pendingFirstBatch: MutableList<T>? = mutableListOf<T>() // null -> no more throttling
    var stopRequestWasScheduled = false
    val mutex = Mutex()

    suspend fun sendFirstBatchIfNeeded() {
      if (isClosedForSend) return

      val firstBatch = pendingFirstBatch
      pendingFirstBatch = null
      if (!firstBatch.isNullOrEmpty()) {
        send(ThrottledAccumulatedItems(firstBatch))
      }
    }

    launch {
      delay(resultThrottlingMs)

      mutex.withLock {
        sendFirstBatchIfNeeded()
      }
    }

    val parentScope = this

    launch {
      originalFlow.collect { item ->
        mutex.withLock {
          val firstBatch = pendingFirstBatch
          if (firstBatch != null) {
            if (shouldPassItem(item)) {
              firstBatch += item
            }
            val stopDelay = shouldStopThrottlingAfterDelay(item, firstBatch.size)
            if (stopDelay != null) {
              if (stopDelay > 0) {
                if (!stopRequestWasScheduled) {
                  stopRequestWasScheduled = true
                  parentScope.launch {
                    delay(stopDelay)

                    mutex.withLock {
                      sendFirstBatchIfNeeded()
                    }
                  }
                }
              }
              else {
                sendFirstBatchIfNeeded()
              }
            }
          }
          else {
            if (shouldPassItem(item)) send(ThrottledOneItem(item))
          }
        }
      }
      mutex.withLock {
        sendFirstBatchIfNeeded()
        close()
      }
    }
  }.buffer(capacity = 0, onBufferOverflow = BufferOverflow.SUSPEND)
}

@ApiStatus.Internal
sealed class ThrottledItems<T>(val items: List<T>)
@ApiStatus.Internal
class ThrottledAccumulatedItems<T>(items: List<T>) : ThrottledItems<T>(items)
@ApiStatus.Internal
class ThrottledOneItem<T>(val item: T) : ThrottledItems<T>(listOf(item))
