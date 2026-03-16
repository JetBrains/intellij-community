// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.util

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

// IO-bounded
private val BUILD_CONCURRENCY: Int = (Runtime.getRuntime().availableProcessors() * 2).coerceIn(4, 16)

suspend fun <T, R> Collection<T>.mapConcurrent(
  concurrency: Int = BUILD_CONCURRENCY,
  action: suspend (T) -> R,
): List<R> {
  require(concurrency > 0) { "Concurrency must be positive, but was $concurrency" }
  if (isEmpty()) {
    return emptyList()
  }

  @Suppress("UNCHECKED_CAST")
  val source = (this as? List<T>) ?: toList()
  if (concurrency == 1 || source.size == 1) {
    return source.mapSequentially(action)
  }

  val result = arrayOfNulls<Any?>(source.size)
  val nextIndex = AtomicInteger(0)
  val workers = minOf(concurrency, source.size)
  coroutineScope {
    repeat(workers) {
      launch {
        while (true) {
          val index = nextIndex.getAndIncrement()
          if (index >= source.size) {
            break
          }
          result[index] = action(source[index])
        }
      }
    }
  }

  @Suppress("UNCHECKED_CAST")
  return result.asList() as List<R>
}

private suspend fun <T, R> List<T>.mapSequentially(action: suspend (T) -> R): List<R> {
  val result = ArrayList<R>(size)
  for (item in this) {
    result.add(action(item))
  }
  return result
}
