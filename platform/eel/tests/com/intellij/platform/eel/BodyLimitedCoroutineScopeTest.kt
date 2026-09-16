// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel

import com.intellij.platform.eel.testFramework.bodyLimitedCoroutineScope
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.io.blockingDispatcher
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.string.shouldStartWith
import io.kotest.matchers.types.instanceOf
import io.kotest.matchers.types.shouldNotBeInstanceOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Test
import org.opentest4j.TestAbortedException
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime

@DelicateCoroutinesApi
@TestApplication  // Everything we need from this annotation is the thread leaker check.
internal class BodyLimitedCoroutineScopeTest {
  @Test
  fun `self cancellation is reported like silent killer`(): Unit = timeoutRunBlocking {
    val error = shouldThrowAny {
      bodyLimitedCoroutineScope {
        cancel("Self cancellation")
        yield()
        error("Should never happen")
      }
    }
    error.message shouldBe "Unexpected CancellationException: java.util.concurrent.CancellationException: Self cancellation"
    error.shouldNotBeInstanceOf<CancellationException>()
  }

  @Test
  fun `all coroutines exit at the end`(): Unit = timeoutRunBlocking {
    val events = ConcurrentHashMap.newKeySet<String>()

    val result = bodyLimitedCoroutineScope {
      events += "body start"

      launch {
        launch {
          events += "job 2 start"
          delay(1.seconds)
          events += "job 2 end"
        }

        events += "job 1 start"
        delay(1.seconds)
        events += "job 1 end"
      }

      launch {
        events += "job 3 start"
        delay(1.seconds)
        events += "job 3 end"
      }

      // In this particular test I'd rather use delays than use CoroutineStart.ATOMOC/UNDISPATCHED. It's closer to real usage.
      delay(100.milliseconds)

      events += "body end"

      123
    }

    events.sorted().joinToString("\n") shouldBe """
      body end
      body start
      job 1 start
      job 2 start
      job 3 start
    """.trimIndent().trim()

    result shouldBe 123
  }

  /**
   * [bodyLimitedCoroutineScope] should be ready for buggy long cancellations.
   * Of course, only if the parent dispatcher is ready for them as well. That's why the context is not a nested single-threaded loop.
   */
  @Test
  fun `ready for buggy cancellations`(): Unit = timeoutRunBlocking(timeout = 6.seconds, context = Dispatchers.IO) {
    val sleepTimeout =
      9.seconds // Bigger than in timeoutRunBlocking to reveal possible errors there, small enough for thread leak checker.
    val innerTestTimeout = 123.milliseconds // Something very small.
    val acceptableLimit = 1.seconds // Something big enough to avoid test flakiness, yet significantly lower than the sleep timeout.

    val duration = measureTime {
      val tce = shouldThrow<TimeoutCancellationException> {
        bodyLimitedCoroutineScope(finalizeTimeout = innerTestTimeout) {
          launch {
            awaitCancellation()
          }.invokeOnCompletion {
            val end = System.nanoTime().nanoseconds + sleepTimeout
            do {
              try {
                Thread.sleep((end - System.nanoTime().nanoseconds).inWholeMilliseconds.coerceAtLeast(0))
              }
              catch (_: InterruptedException) {
                // This nasty buggy code ignores interruptions.
              }
            }
            while (System.nanoTime().nanoseconds < end)
          }
        }
      }
      tce.message shouldBe "Timed out waiting for ${innerTestTimeout.inWholeMilliseconds} ms"
    }

    duration shouldBeLessThan acceptableLimit
  }

  @Test
  fun `aware of silent killers`(): Unit = timeoutRunBlocking {
    val err = shouldThrowAny {
      val evilDeferred = CompletableDeferred<Unit>()
      evilDeferred.cancel("Evil killer")

      bodyLimitedCoroutineScope {
        evilDeferred.await()
      }
    }

    err.message shouldContain "Evil killer"
    err shouldNotBe instanceOf<CancellationException>()
  }

  @Test
  fun `does not miss errors`(): Unit = timeoutRunBlocking(context = blockingDispatcher) {
    val err = shouldThrowAny {
      bodyLimitedCoroutineScope {
        launch(start = CoroutineStart.UNDISPATCHED) {
          try {
            delay(100.milliseconds)
          }
          finally {
            error("Sub error")
          }
        }
        error("Main error")
      }
    }

    val stackTraceToString = err.stackTraceToString()
    stackTraceToString shouldStartWith "java.lang.IllegalStateException: Main error"
    stackTraceToString shouldContain "Suppressed: java.lang.IllegalStateException: Sub error"
  }

  @Test
  fun `terminates scope after first error`(): Unit = timeoutRunBlocking {
    val goodMessage = "The error that should destroy everything"
    val badMessage = "This line should not be called"

    val err = shouldThrowAny {
      bodyLimitedCoroutineScope {
        launch {
          yield()
          error(goodMessage)
        }

        launch {
          delay(100.milliseconds)
          error(badMessage)
        }

        delay(100.milliseconds)
        error("$badMessage (2)")
      }
    }

    val stackTrace = err.stackTraceToString()
    stackTrace shouldContain goodMessage
    stackTrace shouldNotContain badMessage
  }

  @Test
  fun `child failure is propagated while the body waits`(): Unit = timeoutRunBlocking {
    val expectedError = IllegalStateException("Child failure")
    val error = shouldThrow<IllegalStateException> {
      bodyLimitedCoroutineScope {
        launch { throw expectedError }.join()
      }
    }

    error shouldBe expectedError
    error.suppressedExceptions shouldBe emptyList()
  }

  @Test
  fun `child failure is propagated when the body returns`(): Unit = timeoutRunBlocking {
    val expectedError = IllegalStateException("Child failure")
    val error = shouldThrow<IllegalStateException> {
      bodyLimitedCoroutineScope {
        launch(start = CoroutineStart.UNDISPATCHED) { throw expectedError }
      }
    }

    error shouldBe expectedError
    error.suppressedExceptions shouldBe emptyList()
  }

  @Test
  fun `nested test abort is propagated`(): Unit = timeoutRunBlocking {
    val expectedError = TestAbortedException("Unsupported operation")
    val error = shouldThrow<TestAbortedException> {
      bodyLimitedCoroutineScope {
        launch {
          bodyLimitedCoroutineScope {
            throw expectedError
          }
        }.join()
      }
    }

    error shouldBe expectedError
    error.suppressedExceptions shouldBe emptyList()
  }

  @Test
  fun `must be cancellable itself`(): Unit = timeoutRunBlocking {
    shouldNotThrowAny {
      withTimeoutOrNull(10.milliseconds) {
        bodyLimitedCoroutineScope {
          awaitCancellation()
        }
      }
    }
  }
}
