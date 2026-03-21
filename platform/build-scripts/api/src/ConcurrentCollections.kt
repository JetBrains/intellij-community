// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.jetbrains.annotations.ApiStatus.Internal
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.EmptyCoroutineContext

private val BUILD_CONCURRENCY: Int = (Runtime.getRuntime().availableProcessors() * 2).coerceIn(4, 16)
private const val WORK_CHUNK_SIZE: Int = 4

@Internal
suspend fun <T, R> Collection<T>.mapConcurrent(
  concurrency: Int = BUILD_CONCURRENCY,
  workerDispatcher: CoroutineDispatcher? = null,
  action: suspend (T) -> R,
): List<R> {
  require(concurrency > 0) { "Concurrency must be positive, but was $concurrency" }
  if (isEmpty()) {
    return emptyList()
  }

  @Suppress("UNCHECKED_CAST")
  val source = (this as? List<T>) ?: toList()
  return when {
    concurrency == 1 || source.size == 1 -> {
      if (workerDispatcher == null) {
        mapSequentially(source, action)
      }
      else {
        withContext(workerDispatcher) {
          mapSequentially(source, action)
        }
      }
    }
    source.size <= concurrency -> source.mapWithCoroutinePerItem(workerDispatcher = workerDispatcher, action = action)
    else -> mapWithWorkerPool(source, concurrency = concurrency, workerDispatcher = workerDispatcher, action = action)
  }
}

private suspend fun <T, R> mapSequentially(list: List<T>, action: suspend (T) -> R): List<R> {
  val result = ArrayList<R>(list.size)
  for (item in list) {
    result.add(action(item))
  }
  return result
}

private suspend fun <T, R> List<T>.mapWithCoroutinePerItem(
  workerDispatcher: CoroutineDispatcher?,
  action: suspend (T) -> R,
): List<R> {
  val result = arrayOfNulls<Any?>(size)
  val workerContext = workerDispatcher ?: EmptyCoroutineContext
  coroutineScope {
    val scopeJob = coroutineContext.job
    forEachIndexed { index, item ->
      yield()
      launch(workerContext) {
        try {
          result[index] = action(item)
        }
        catch (e: CancellationException) {
          scopeJob.cancel(e)
          throw e
        }
      }
    }
  }

  @Suppress("UNCHECKED_CAST")
  return result.asList() as List<R>
}

private suspend fun <T, R> mapWithWorkerPool(
  list: List<T>,
  concurrency: Int,
  workerDispatcher: CoroutineDispatcher?,
  action: suspend (T) -> R,
): List<R> {
  val result = arrayOfNulls<Any?>(list.size)
  val nextIndex = AtomicInteger(0)
  val workerContext = workerDispatcher ?: EmptyCoroutineContext
  coroutineScope {
    val scopeJob = coroutineContext.job
    repeat(concurrency) {
      launch(workerContext) {
        while (true) {
          val startIndex = nextIndex.getAndAdd(WORK_CHUNK_SIZE)
          if (startIndex >= list.size) {
            break
          }

          val endIndex = minOf(startIndex + WORK_CHUNK_SIZE, list.size)
          for (index in startIndex until endIndex) {
            try {
              result[index] = action(list[index])
            }
            catch (e: CancellationException) {
              scopeJob.cancel(e)
              throw e
            }
          }
          yield()
        }
      }
    }
  }

  @Suppress("UNCHECKED_CAST")
  return result.asList() as List<R>
}
