// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util.async

import kotlinx.coroutines.*
import kotlin.coroutines.coroutineContext

suspend fun <T : Any> spin(delayStrategy: DelayStrategy, body: suspend CoroutineScope.(attempt: Int) -> T?): T {
  var curDelayMs = 0L
  var attempt = 0
  while (coroutineContext.job.isActive) {
    val res = coroutineScope {
      launch {
        curDelayMs = delayStrategy.nextDelay(curDelayMs)
        delay(curDelayMs)
        attempt++
      }.use { job ->
        val res = coroutineScope { body(attempt) }
        if (res == null) {
          job.join()
        }
        res
      }
    }
    if (res != null) {
      return res
    }
  }
  yield()
  error("unreachable")
}

fun interface DelayStrategy {
  fun nextDelay(delay: Long): Long

  companion object {
    fun exponential(minDelayMs: Long, maxDelayMs: Long): DelayStrategy =
      DelayStrategy { delay -> (delay * 2).coerceIn(minDelayMs, maxDelayMs) }

    fun constant(constant: Long) = DelayStrategy { constant }
  }
}