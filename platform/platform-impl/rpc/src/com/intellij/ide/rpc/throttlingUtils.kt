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
@OptIn(DelicateCoroutinesApi::class)
@ApiStatus.Internal
fun <T> Flow<T>.throttledWithAccumulation(resultThrottlingMs: Long = DEFAULT_RESULT_THROTTLING_MS, resultCountToStopThrottling: Int = DEFAULT_RESULT_COUNT_TO_STOP_THROTTLING): Flow<ThrottledItems<T>> {
  val originalFlow = this
  return channelFlow {
    var pendingFirstBatch: MutableList<T>? = mutableListOf<T>() // null -> no more throttling
    val mutex = Mutex()
    suspend fun sendFirstBatchIfNeeded() {
      mutex.withLock {
        if (isClosedForSend) return

        val firstBatch = pendingFirstBatch
        pendingFirstBatch = null
        if (!firstBatch.isNullOrEmpty()) {
          send(ThrottledAccumulatedItems(firstBatch))
        }
      }
    }
    launch {
      delay(resultThrottlingMs)
      sendFirstBatchIfNeeded()
    }
    launch {
      originalFlow.collect { item ->
        mutex.withLock {
          val firstBatch = pendingFirstBatch
          if (firstBatch != null) {
            firstBatch += item
            if (firstBatch.size >= resultCountToStopThrottling) {
              pendingFirstBatch = null
              send(ThrottledAccumulatedItems(firstBatch))
            }
          }
          else {
            send(ThrottledOneItem(item))
          }
        }
      }
      sendFirstBatchIfNeeded()
      mutex.withLock {
        close()
      }
    }
  }.buffer(capacity = 0, onBufferOverflow = BufferOverflow.SUSPEND)
}

@ApiStatus.Internal
sealed interface ThrottledItems<T>
@ApiStatus.Internal
class ThrottledAccumulatedItems<T>(val items: List<T>) : ThrottledItems<T>
@ApiStatus.Internal
class ThrottledOneItem<T>(val item: T) : ThrottledItems<T>
