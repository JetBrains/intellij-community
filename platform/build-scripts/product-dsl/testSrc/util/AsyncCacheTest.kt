// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.util

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jetbrains.intellij.build.runBlockingOnVirtualThreads
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.util.concurrent.CancellationException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@ExtendWith(org.jetbrains.intellij.build.productLayout.TestFailureLogger::class)
class AsyncCacheTest {
  @Test
  fun `basic caching - value is loaded once and cached`() {
    val loadCount = AtomicInteger(0)
    val cache = AsyncCache<String, String>()

    val result1 = cache.getOrPut("key") {
      loadCount.incrementAndGet()
      "value"
    }
    val result2 = cache.getOrPut("key") {
      loadCount.incrementAndGet()
      "should-not-be-called"
    }

    assertThat(result1).isEqualTo("value")
    assertThat(result2).isEqualTo("value")
    assertThat(loadCount.get()).isEqualTo(1)
  }

  @Test
  fun `concurrent requests for same key share computation`() {
    runBlockingOnVirtualThreads {
      val loadCount = AtomicInteger(0)
      val cache = AsyncCache<String, String>()

      val deferreds = (1..10).map {
        async {
          cache.getOrPut("shared-key") {
            loadCount.incrementAndGet()
            Thread.sleep(50)
            "computed-value"
          }
        }
      }

      val results = deferreds.awaitAll()

      // All should get the same result
      assertThat(results).allMatch { it == "computed-value" }
      // But loader should only be called once
      assertThat(loadCount.get()).isEqualTo(1)
    }
  }

  @Test
  fun `null values are cached correctly`() {
    val loadCount = AtomicInteger(0)
    val cache = AsyncCache<String, String?>()

    val result1 = cache.getOrPut("nullable-key") {
      loadCount.incrementAndGet()
      null
    }
    val result2 = cache.getOrPut("nullable-key") {
      loadCount.incrementAndGet()
      "should-not-be-called"
    }

    assertThat(result1).isNull()
    assertThat(result2).isNull()
    assertThat(loadCount.get()).isEqualTo(1)
  }

  @Test
  fun `exceptions ARE cached - subsequent calls get same exception`() {
    val loadCount = AtomicInteger(0)
    val cache = AsyncCache<String, String>()

    // First call fails
    assertThatThrownBy {
      cache.getOrPut("failing-key") {
        loadCount.incrementAndGet()
        throw IllegalStateException("Load failed")
      }
    }
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessage("Load failed")
    assertThat(loadCount.get()).isEqualTo(1)

    // Second call gets SAME cached failure - no retry
    assertThatThrownBy {
      cache.getOrPut("failing-key") {
        loadCount.incrementAndGet()
        "should-not-be-called"
      }
    }
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessage("Load failed")
    assertThat(loadCount.get()).isEqualTo(1) // NO retry - still 1
  }

  @Test
  fun `different keys are cached independently`() {
    val cache = AsyncCache<String, Int>()

    val result1 = cache.getOrPut("key1") { 1 }
    val result2 = cache.getOrPut("key2") { 2 }
    val result3 = cache.getOrPut("key1") { 999 } // Should return cached value

    assertThat(result1).isEqualTo(1)
    assertThat(result2).isEqualTo(2)
    assertThat(result3).isEqualTo(1) // Not 999
  }

  @Test
  fun `concurrent access with different keys works correctly`() {
    runBlockingOnVirtualThreads {
      val cache = AsyncCache<Int, String>()

      val deferreds = (1..100).map { key ->
        async {
          cache.getOrPut(key) {
            Thread.sleep(10)
            "value-$key"
          }
        }
      }

      val results = deferreds.awaitAll()

      // Each key should have its own value
      results.forEachIndexed { index, value ->
        assertThat(value).isEqualTo("value-${index + 1}")
      }
    }
  }

  @Test
  fun `failed computation with concurrent waiters - all see exception`() {
    runBlockingOnVirtualThreads {
      val loadCount = AtomicInteger(0)
      val cache = AsyncCache<String, String>()

      val deferreds = (1..5).map {
        async {
          assertThatThrownBy {
            cache.getOrPut("failing-key") {
              val count = loadCount.incrementAndGet()
              Thread.sleep(20)
              throw IllegalStateException("Failed attempt $count")
            }
          }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageStartingWith("Failed attempt")
          "caught-exception"
        }
      }

      val results = deferreds.awaitAll()

      // All concurrent requests should see the exception
      assertThat(results).allMatch { it == "caught-exception" }
      // Only one should have tried (they shared the computation)
      assertThat(loadCount.get()).isEqualTo(1)

      assertThatThrownBy {
        cache.getOrPut("failing-key") {
          loadCount.incrementAndGet()
          "should-not-be-called"
        }
      }
        .isInstanceOf(IllegalStateException::class.java)
        .hasMessageStartingWith("Failed attempt")
      assertThat(loadCount.get()).isEqualTo(1) // Still 1 - failure is cached
    }
  }

  @Test
  fun `rapid sequential access uses cache`() {
    val loadCount = AtomicInteger(0)
    val cache = AsyncCache<String, Int>()

    repeat(1000) {
      val result = cache.getOrPut("rapid-key") {
        loadCount.incrementAndGet()
        42
      }
      assertThat(result).isEqualTo(42)
    }

    assertThat(loadCount.get()).isEqualTo(1)
  }

  @Test
  fun `complex value types are cached correctly`() {
    val cache = AsyncCache<String, List<String>>()
    val expected = listOf("a", "b", "c")

    val result1 = cache.getOrPut("complex") { expected }
    val result2 = cache.getOrPut("complex") { listOf("x", "y", "z") }

    assertThat(result1).isEqualTo(expected)
    assertThat(result2).isSameAs(result1) // Should be the exact same cached instance
  }

  @Test
  fun `fails fast on direct recursive await for same key`() {
    val cache = AsyncCache<String, Int>()

    assertFailsFast {
      // the timeout bounds the test: a guard that does not fire would deadlock on the entry of the caller
      cache.getOrPut("loop", timeout = 10.seconds) {
        cache.getOrPut("loop", timeout = 10.seconds) { 42 }
      }
    }
  }

  @Test
  fun `fails fast on child coroutine recursive await for same key`() {
    val cache = AsyncCache<String, Int>()

    assertFailsFast {
      cache.getOrPut("loop", timeout = 10.seconds) {
        // the guard travels into the coroutine, see `singleFlightContextElement`
        runBlockingOnVirtualThreads {
          coroutineScope {
            async {
              cache.getOrPut("loop", timeout = 10.seconds) { 42 }
            }.await()
          }
        }
      }
    }
  }

  @Test
  fun `close cancels pending computations and processes completed values`() {
    val cache = AsyncCache<String, String>()
    val started = CountDownLatch(1)
    val release = CountDownLatch(1)
    val failure = arrayOfNulls<Throwable>(1)
    val pending = Thread.ofVirtual().start {
      try {
        cache.getOrPut("pending") {
          started.countDown()
          release.await()
          "pending"
        }
      }
      catch (t: Throwable) {
        failure[0] = t
      }
    }

    assertThat(started.await(5, TimeUnit.SECONDS)).isTrue()
    assertThat(cache.getOrPut("completed") { "completed" }).isEqualTo("completed")

    val completed = ArrayList<String>()
    cache.close { completed.add(it) }
    release.countDown()
    pending.join()

    assertThat(completed).containsExactly("completed")
    assertThat(failure[0]).isInstanceOf(CancellationException::class.java)
  }

  /**
   * A timeout is the only way a caller stops waiting now. It must leave the entry alone, so the computation still
   * serves every other caller.
   */
  @Test
  fun `a caller that times out leaves the computation for the others`() {
    val cache = AsyncCache<String, String>()
    val loadCount = AtomicInteger(0)
    val started = CountDownLatch(1)
    val release = CountDownLatch(1)

    assertThatThrownBy {
      cache.getOrPut("key", timeout = 100.milliseconds) {
        loadCount.incrementAndGet()
        started.countDown()
        release.await()
        "value"
      }
    }.isInstanceOf(TimeoutException::class.java)

    assertThat(started.await(5, TimeUnit.SECONDS)).isTrue()
    release.countDown()

    assertThat(cache.getOrPut("key") { "should-not-be-called" }).isEqualTo("value")
    assertThat(loadCount.get()).isEqualTo(1)
  }

  private fun assertFailsFast(block: () -> Unit) {
    assertThatThrownBy { block() }
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessageContaining("Recursive await")
      .hasMessageContaining("loop")
  }
}
