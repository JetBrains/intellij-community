// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util.async

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

suspend fun <T : Any> spin(delayStrategy: DelayStrategy, body: suspend CoroutineScope.(attempt: Int) -> T?): T {
  var curDelay = Duration.ZERO
  var attempt = 0
  while (currentCoroutineContext().job.isActive) {
    val res = coroutineScope {
      launch {
        curDelay = delayStrategy.nextDelay(curDelay)
        delay(curDelay)
      }.use { job ->
        val res = coroutineScope { body(attempt) }
        if (res == null) {
          job.join()
        }
        res
      }.also {
        attempt++
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
  fun nextDelay(delay: Duration): Duration

  companion object {
    fun linear(minDelay: Duration = 1.seconds, maxDelay: Duration = Duration.INFINITE, step: Duration = 1.seconds): DelayStrategy =
      DelayStrategy { delay -> (delay + step).coerceIn(minDelay, maxDelay) }

    fun exponential(minDelay: Duration = 1.seconds, maxDelay: Duration = Duration.INFINITE, multiplier: Double = 2.0): DelayStrategy =
      DelayStrategy { delay -> (delay * multiplier).coerceIn(minDelay, maxDelay) }

    fun constant(delay: Duration) = DelayStrategy { delay }
  }
}