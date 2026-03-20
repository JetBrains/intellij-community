// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@ExtendWith(org.jetbrains.intellij.build.productLayout.TestFailureLogger::class)
class AsyncCacheTest {
  @Test
  fun `basic caching - value is loaded once and cached`() {
    runBlocking(Dispatchers.Default) {
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
  }

  @Test
  fun `concurrent requests for same key share computation`() {
    runBlocking(Dispatchers.Default) {
      val loadCount = AtomicInteger(0)
      val cache = AsyncCache<String, String>()

      // Launch 10 concurrent requests for the same key
      val deferreds = (1..10).map {
        async {
          cache.getOrPut("shared-key") {
            loadCount.incrementAndGet()
            delay(50.milliseconds) // Simulate some work
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
    runBlocking(Dispatchers.Default) {
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
  }

  @Test
  fun `exceptions ARE cached - subsequent calls get same exception`() {
    runBlocking(Dispatchers.Default) {
      val loadCount = AtomicInteger(0)
      val cache = AsyncCache<String, String>()

      // First call fails
      assertThatThrownBy {
        runBlocking {
          cache.getOrPut("failing-key") {
            loadCount.incrementAndGet()
            throw IllegalStateException("Load failed")
          }
        }
      }
        .isInstanceOf(IllegalStateException::class.java)
        .hasMessage("Load failed")
      assertThat(loadCount.get()).isEqualTo(1)

      // Second call gets SAME cached failure - no retry
      assertThatThrownBy {
        runBlocking {
          cache.getOrPut("failing-key") {
            loadCount.incrementAndGet()
            "should-not-be-called"
          }
        }
      }
        .isInstanceOf(IllegalStateException::class.java)
        .hasMessage("Load failed")
      assertThat(loadCount.get()).isEqualTo(1) // NO retry - still 1
    }
  }

  @Test
  fun `different keys are cached independently`() {
    runBlocking(Dispatchers.Default) {
      val cache = AsyncCache<String, Int>()

      val result1 = cache.getOrPut("key1") { 1 }
      val result2 = cache.getOrPut("key2") { 2 }
      val result3 = cache.getOrPut("key1") { 999 } // Should return cached value

      assertThat(result1).isEqualTo(1)
      assertThat(result2).isEqualTo(2)
      assertThat(result3).isEqualTo(1) // Not 999
    }
  }

  @Test
  fun `concurrent access with different keys works correctly`() {
    runBlocking(Dispatchers.Default) {
      val cache = AsyncCache<Int, String>()

      val deferreds = (1..100).map { key ->
        async {
          cache.getOrPut(key) {
            delay(10.milliseconds)
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
    runBlocking(Dispatchers.Default) {
      val loadCount = AtomicInteger(0)
      val cache = AsyncCache<String, String>()

      // Launch multiple concurrent requests that will all fail
      val deferreds = (1..5).map {
        async {
          assertThatThrownBy {
            runBlocking {
              cache.getOrPut("failing-key") {
                val count = loadCount.incrementAndGet()
                delay(20.milliseconds)
                throw IllegalStateException("Failed attempt $count")
              }
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
        runBlocking {
          cache.getOrPut("failing-key") {
            loadCount.incrementAndGet()
            "should-not-be-called"
          }
        }
      }
        .isInstanceOf(IllegalStateException::class.java)
        .hasMessageStartingWith("Failed attempt")
      assertThat(loadCount.get()).isEqualTo(1) // Still 1 - failure is cached
    }
  }

  @Test
  fun `cache works in nested scope`() {
    runBlocking(Dispatchers.Default) {
      coroutineScope {
        val cache = AsyncCache<String, String>()

        val result = cache.getOrPut("key") { "value" }

        assertThat(result).isEqualTo("value")
      }
    }
  }

  @Test
  fun `rapid sequential access uses cache`() {
    runBlocking(Dispatchers.Default) {
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
  }

  @Test
  fun `complex value types are cached correctly`() {
    runBlocking(Dispatchers.Default) {
      val cache = AsyncCache<String, List<String>>()
      val expected = listOf("a", "b", "c")

      val result1 = cache.getOrPut("complex") { expected }
      val result2 = cache.getOrPut("complex") { listOf("x", "y", "z") }

      assertThat(result1).isEqualTo(expected)
      assertThat(result2).isSameAs(result1) // Should be the exact same cached instance
    }
  }

  @Test
  fun `fails fast on direct recursive await for same key`() {
    runBlocking(Dispatchers.Default) {
      val cache = AsyncCache<String, Int>()

      assertFailsFast {
        cache.getOrPut("loop") {
          cache.getOrPut("loop") { 42 }
        }
      }
    }
  }

  @Test
  fun `fails fast on child coroutine recursive await for same key`() {
    runBlocking(Dispatchers.Default) {
      val cache = AsyncCache<String, Int>()

      assertFailsFast {
        cache.getOrPut("loop") {
          coroutineScope {
            async {
              cache.getOrPut("loop") { 42 }
            }.await()
          }
        }
      }
    }
  }

  @Test
  fun `close cancels pending computations and processes completed values`() {
    runBlocking(Dispatchers.Default) {
      val cache = AsyncCache<String, String>()
      val started = CompletableDeferred<Unit>()
      val release = CompletableDeferred<Unit>()
      val pending = async {
        cache.getOrPut("pending") {
          started.complete(Unit)
          release.await()
          "pending"
        }
      }

      started.await()
      assertThat(cache.getOrPut("completed") { "completed" }).isEqualTo("completed")

      val completed = ArrayList<String>()
      cache.close { completed.add(it) }
      release.complete(Unit)

      var failure: Throwable? = null
      try {
        pending.await()
      }
      catch (t: Throwable) {
        failure = t
      }

      assertThat(completed).containsExactly("completed")
      assertThat(failure).isInstanceOf(CancellationException::class.java)
    }
  }

  @Test
  fun `owner cancellation evicts entry and next caller retries`() {
    runBlocking(Dispatchers.Default) {
      val cache = AsyncCache<String, String>()
      val loadCount = AtomicInteger(0)
      val started = CompletableDeferred<Unit>()
      val release = CompletableDeferred<Unit>()
      val firstAttempt = async {
        cache.getOrPut("key") {
          val attempt = loadCount.incrementAndGet()
          if (attempt == 1) {
            started.complete(Unit)
            release.await()
          }
          "value-$attempt"
        }
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
      assertThat(
        withTimeout(5.seconds) {
          cache.getOrPut("key") {
            val attempt = loadCount.incrementAndGet()
            "value-$attempt"
          }
        }
      ).isEqualTo("value-2")
      assertThat(loadCount.get()).isEqualTo(2)
    }
  }

  @Test
  fun `non-owner waiter cancellation does not poison cached computation`() {
    runBlocking(Dispatchers.Default) {
      val cache = AsyncCache<String, String>()
      val loadCount = AtomicInteger(0)
      val started = CompletableDeferred<Unit>()
      val release = CompletableDeferred<Unit>()
      val owner = async {
        cache.getOrPut("key") {
          loadCount.incrementAndGet()
          started.complete(Unit)
          release.await()
          "value"
        }
      }

      withTimeout(5.seconds) {
        started.await()
      }

      val waiter = async {
        cache.getOrPut("key") { "should-not-be-called" }
      }
      waiter.cancel()

      var failure: Throwable? = null
      try {
        waiter.await()
      }
      catch (t: Throwable) {
        failure = t
      }

      assertThat(failure).isInstanceOf(CancellationException::class.java)
      release.complete(Unit)
      assertThat(owner.await()).isEqualTo("value")
      assertThat(
        withTimeout(5.seconds) {
          cache.getOrPut("key") { "should-not-be-called" }
        }
      ).isEqualTo("value")
      assertThat(loadCount.get()).isEqualTo(1)
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
      .hasMessageContaining("loop")
  }
}
