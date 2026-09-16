// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import com.intellij.platform.buildScripts.concurrency.taskScope
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicInteger

/** The number of workers of a fan-out in the build. A caller that runs its own workers bounds them with it too. */
@ApiStatus.Internal
val BUILD_CONCURRENCY: Int = (Runtime.getRuntime().availableProcessors() * 2).coerceIn(4, 16)

/**
 * Runs [action] with one permit of the semaphore, and returns the permit when [action] ends.
 *
 * The calling thread waits for a permit before it runs [action].
 */
@ApiStatus.Internal
inline fun <T> Semaphore.withPermit(action: () -> T): T {
  acquire()
  try {
    return action()
  }
  finally {
    release()
  }
}

/**
 * Runs [action] for every item on up to [concurrency] virtual threads, and blocks until all of them have ended.
 *
 * The first failure cancels the other workers. The scope reports it as a task failure.
 */
@ApiStatus.Internal
fun <T> Collection<T>.forEachConcurrent(
  concurrency: Int = BUILD_CONCURRENCY,
  action: (T) -> Unit,
) {
  runOnVirtualThreads(items = asList(), concurrency = concurrency) { _, item -> action(item) }
}

/**
 * Maps every item with [action] on up to [concurrency] virtual threads, and returns the results in the order of the items.
 *
 * The first failure cancels the other workers. See [forEachConcurrent].
 */
@ApiStatus.Internal
fun <T, R> Collection<T>.mapConcurrent(
  concurrency: Int = BUILD_CONCURRENCY,
  action: (T) -> R,
): List<R> {
  val items = asList()
  val result = arrayOfNulls<Any?>(items.size)
  runOnVirtualThreads(items = items, concurrency = concurrency) { index, item -> result[index] = action(item) }
  @Suppress("UNCHECKED_CAST")
  return result.asList() as List<R>
}

private fun <T> Collection<T>.asList(): List<T> = this as? List<T> ?: toList()

/**
 * The workers take the next index from a shared counter, so a slow item holds one worker and not a fixed chunk.
 */
private fun <T> runOnVirtualThreads(items: List<T>, concurrency: Int, action: (Int, T) -> Unit) {
  require(concurrency > 0) { "Concurrency must be positive, but was $concurrency" }
  if (items.isEmpty()) {
    return
  }

  val name = Thread.currentThread().name.ifEmpty { "concurrent" }
  val nextIndex = AtomicInteger()
  taskScope {
    repeat(minOf(concurrency, items.size)) { worker ->
      fork("$name worker $worker") {
        while (true) {
          checkCancelled()
          val index = nextIndex.getAndIncrement()
          if (index >= items.size) {
            break
          }
          checkCancelled()
          action(index, items[index])
        }
      }
    }
    join()
  }
}
