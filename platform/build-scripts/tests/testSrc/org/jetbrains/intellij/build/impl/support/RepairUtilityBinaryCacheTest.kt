// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.support

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jetbrains.intellij.build.BuildContext
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

internal class RepairUtilityBinaryCacheTest {
  @Test
  fun `concurrent requests for the same context share computation`() {
    runBlocking(Dispatchers.Default) {
      val invocationCount = AtomicInteger()
      val context = buildContext()
      val cache = BuildContextSingleFlightCache("repair utility test") {
        invocationCount.incrementAndGet()
        delay(20.milliseconds)
        42
      }

      val values = List(8) {
        async {
          cache.getOrLoad(context)
        }
      }.awaitAll()

      assertThat(values).containsOnly(42)
      assertThat(invocationCount.get()).isEqualTo(1)
    }
  }

  @Test
  fun `sequential requests for the same context reuse the cached value`() {
    runBlocking(Dispatchers.Default) {
      val invocationCount = AtomicInteger()
      val context = buildContext()
      val cache = BuildContextSingleFlightCache("repair utility test") {
        invocationCount.incrementAndGet()
      }

      val first = cache.getOrLoad(context)
      val second = cache.getOrLoad(context)

      assertThat(first).isEqualTo(1)
      assertThat(second).isEqualTo(1)
      assertThat(invocationCount.get()).isEqualTo(1)
    }
  }

  @Test
  fun `different contexts do not share cached values`() {
    runBlocking(Dispatchers.Default) {
      val invocationCount = AtomicInteger()
      val firstContext = buildContext()
      val secondContext = buildContext()
      val cache = BuildContextSingleFlightCache("repair utility test") {
        invocationCount.incrementAndGet()
      }

      val first = cache.getOrLoad(firstContext)
      val second = cache.getOrLoad(secondContext)
      val firstAgain = cache.getOrLoad(firstContext)

      assertThat(first).isEqualTo(1)
      assertThat(second).isEqualTo(2)
      assertThat(firstAgain).isEqualTo(1)
      assertThat(invocationCount.get()).isEqualTo(2)
    }
  }

  @Test
  fun `failures are cached per context`() {
    val invocationCount = AtomicInteger()
    val context = buildContext()
    val cache = BuildContextSingleFlightCache("repair utility test") {
      invocationCount.incrementAndGet()
      error("boom")
    }

    repeat(2) {
      assertThatThrownBy {
        runBlocking(Dispatchers.Default) {
          cache.getOrLoad(context)
        }
      }
        .isInstanceOf(IllegalStateException::class.java)
        .hasMessageContaining("boom")
    }

    assertThat(invocationCount.get()).isEqualTo(1)
  }

  @Test
  fun `recursive await fails fast`() {
    val context = buildContext()
    lateinit var cache: BuildContextSingleFlightCache<Int>
    cache = BuildContextSingleFlightCache("repair utility test") {
      cache.getOrLoad(it)
    }

    assertFailsFast {
      cache.getOrLoad(context)
    }
  }

  @Test
  fun `recursive await from child coroutine fails fast`() {
    val context = buildContext()
    lateinit var cache: BuildContextSingleFlightCache<Int>
    cache = BuildContextSingleFlightCache("repair utility test") {
      coroutineScope {
        async {
          cache.getOrLoad(it)
        }.await()
      }
    }

    assertFailsFast {
      cache.getOrLoad(context)
    }
  }

  private fun buildContext(): BuildContext = mock(BuildContext::class.java)

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
