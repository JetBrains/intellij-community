// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class SuspendingLazyTest {
  @Test
  fun computesValueOnceForConcurrentAwaiters() {
    val invocationCount = AtomicInteger()
    val lazyValue = suspendingLazy("test value") {
      invocationCount.incrementAndGet()
      delay(20.milliseconds)
      42
    }

    val values = runBlocking(Dispatchers.Default) {
      List(8) {
        async {
          lazyValue.await()
        }
      }.awaitAll()
    }

    assertThat(values).containsOnly(42)
    assertThat(invocationCount.get()).isEqualTo(1)
  }

  @Test
  fun reusesInitializerFailure() {
    val invocationCount = AtomicInteger()
    val lazyValue = suspendingLazy<Int>("failing value") {
      invocationCount.incrementAndGet()
      error("boom")
    }

    repeat(2) {
      assertThatThrownBy {
        runBlocking {
          lazyValue.await()
        }
      }
        .isInstanceOf(IllegalStateException::class.java)
        .hasMessageContaining("boom")
    }
    assertThat(invocationCount.get()).isEqualTo(1)
  }

  @Test
  fun retriesAfterCancellation() {
    val invocationCount = AtomicInteger()
    val started = CompletableDeferred<Unit>()
    val release = CompletableDeferred<Unit>()
    val lazyValue = suspendingLazy("cancellable value") {
      val attempt = invocationCount.incrementAndGet()
      if (attempt == 1) {
        started.complete(Unit)
        release.await()
      }
      40 + attempt
    }

    runBlocking(Dispatchers.Default) {
      val firstAttempt = async {
        lazyValue.await()
      }

      withTimeout(5.seconds) {
        started.await()
      }
      firstAttempt.cancel()

      var failure: Throwable? = null
      try {
        firstAttempt.await()
      }
      catch (t: Throwable) {
        failure = t
      }

      assertThat(failure).isInstanceOf(CancellationException::class.java)
      release.complete(Unit)
      assertThat(lazyValue.await()).isEqualTo(42)
    }

    assertThat(invocationCount.get()).isEqualTo(2)
  }

  @Test
  fun failsFastOnRecursiveAwait() {
    lateinit var lazyValue: SuspendingLazy<Int>
    lazyValue = suspendingLazy("recursive value") {
      lazyValue.await()
    }

    assertFailsFast {
      lazyValue.await()
    }
  }

  @Test
  fun failsFastOnRecursiveAwaitFromChildCoroutine() {
    lateinit var lazyValue: SuspendingLazy<Int>
    lazyValue = suspendingLazy("recursive value") {
      coroutineScope {
        async {
          lazyValue.await()
        }.await()
      }
    }

    assertFailsFast {
      lazyValue.await()
    }
  }

  private fun assertFailsFast(block: suspend () -> Unit) {
    assertThatThrownBy {
      runBlocking(Dispatchers.Default) {
        withTimeout(1.seconds) {
          block()
        }
      }
    }
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessageContaining("Recursive await")
  }
}
