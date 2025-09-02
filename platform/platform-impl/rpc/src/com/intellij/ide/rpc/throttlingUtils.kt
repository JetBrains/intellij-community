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
  throttledWithAccumulation(resultThrottlingMs, shouldPassItem, 0, { false }) { _, accumulatedSize ->
    if (accumulatedSize >= resultCountToStopThrottling) 0 else null
  }

@OptIn(DelicateCoroutinesApi::class)
@ApiStatus.Internal
fun <T> Flow<T>.throttledWithAccumulation(resultThrottlingMs: Long = DEFAULT_RESULT_THROTTLING_MS,
                                          shouldPassItem: (T) -> Boolean,
                                          fastPassThrottlingMs: Long,
                                          shouldFastPassItem: (T) -> Boolean,
                                          shouldStopThrottlingAfterDelay: (T, Int) -> Long?): Flow<ThrottledItems<T>> {
  val originalFlow = this
  return channelFlow {
    var pendingFastPassBatch: MutableList<T>? = mutableListOf()
    var pendingBatch: MutableList<T>? = mutableListOf() // null -> no more throttling
    var stopRequestWasScheduled = false
    var sendFastPassBatchWasScheduled = false
    val mutex = Mutex()

    fun getAndMarkSentFastPassBatch(): List<T> {
      if (isClosedForSend) return emptyList()

      val batch = pendingFastPassBatch
      pendingFastPassBatch = null
      return batch ?: emptyList()
    }

    suspend fun sendFastPassBatchIfNeeded() {
      if (isClosedForSend) return

      val batch = getAndMarkSentFastPassBatch()
      if (!batch.isEmpty()) {
        send(ThrottledAccumulatedItems(batch))
      }
    }

    suspend fun sendBatchIfNeeded() {
      if (isClosedForSend) return

      val batch = getAndMarkSentFastPassBatch() + (pendingBatch ?: emptyList())
      pendingBatch = null
      if (!batch.isEmpty()) {
        send(ThrottledAccumulatedItems(batch))
      }
    }

    launch {
      delay(resultThrottlingMs)

      mutex.withLock {
        sendBatchIfNeeded()
      }
    }

    val parentScope = this

    launch {
      originalFlow.collect { item ->
        mutex.withLock {
          val batch = pendingBatch

          if (batch != null) {
            val fastPassBatch = pendingFastPassBatch

            if (shouldPassItem(item)) {
              if (fastPassBatch != null && shouldFastPassItem(item)) {
                fastPassBatch += item

                // Schedule sending fast pass batch only once after the first fast pass item came
                if (!sendFastPassBatchWasScheduled) {
                  sendFastPassBatchWasScheduled = true
                  parentScope.launch {
                    delay(fastPassThrottlingMs)

                    mutex.withLock {
                      sendFastPassBatchIfNeeded()
                    }
                  }
                }
              }
              else {
                batch += item
              }
            }

            val stopDelay = shouldStopThrottlingAfterDelay(item, batch.size + (fastPassBatch?.size ?: 0))
            if (stopDelay != null) {
              if (stopDelay > 0) {
                if (!stopRequestWasScheduled) {
                  stopRequestWasScheduled = true
                  parentScope.launch {
                    delay(stopDelay)

                    mutex.withLock {
                      sendBatchIfNeeded()
                    }
                  }
                }
              }
              else {
                sendBatchIfNeeded()
              }
            }
          }
          else {
            if (shouldPassItem(item)) send(ThrottledOneItem(item))
          }
        }
      }
      mutex.withLock {
        sendBatchIfNeeded()
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
