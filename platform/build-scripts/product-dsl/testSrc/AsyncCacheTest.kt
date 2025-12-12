// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

class AsyncCacheTest {
  @Test
  fun `basic caching - value is loaded once and cached`(): Unit = runBlocking(Dispatchers.Default) {
    val loadCount = AtomicInteger(0)
    val cache = AsyncCache<String, String>(this)

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
  fun `concurrent requests for same key share computation`(): Unit = runBlocking(Dispatchers.Default) {
    val loadCount = AtomicInteger(0)
    val cache = AsyncCache<String, String>(this)

    // Launch 10 concurrent requests for the same key
    val deferreds = (1..10).map {
      async {
        cache.getOrPut("shared-key") {
          loadCount.incrementAndGet()
          delay(50) // Simulate some work
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

  @Test
  fun `null values are cached correctly`(): Unit = runBlocking(Dispatchers.Default) {
    val loadCount = AtomicInteger(0)
    val cache = AsyncCache<String, String?>(this)

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
    val cache = AsyncCache<String, String>(CoroutineScope(Dispatchers.Default))

    // First call fails
    val exception1 = Assertions.assertThrows(IllegalStateException::class.java) {
      runBlocking {
        cache.getOrPut("failing-key") {
          loadCount.incrementAndGet()
          throw IllegalStateException("Load failed")
        }
      }
    }

    Assertions.assertEquals("Load failed", exception1.message)
    Assertions.assertEquals(1, loadCount.get())

    // Second call gets SAME cached failure - no retry
    val exception2 = Assertions.assertThrows(IllegalStateException::class.java) {
      runBlocking {
        cache.getOrPut("failing-key") {
          loadCount.incrementAndGet()
          "should-not-be-called"
        }
      }
    }

    Assertions.assertEquals("Load failed", exception2.message)
    Assertions.assertEquals(1, loadCount.get()) // NO retry - still 1
  }

  @Test
  fun `different keys are cached independently`(): Unit = runBlocking(Dispatchers.Default) {
    val cache = AsyncCache<String, Int>(this)

    val result1 = cache.getOrPut("key1") { 1 }
    val result2 = cache.getOrPut("key2") { 2 }
    val result3 = cache.getOrPut("key1") { 999 } // Should return cached value

    assertThat(result1).isEqualTo(1)
    assertThat(result2).isEqualTo(2)
    assertThat(result3).isEqualTo(1) // Not 999
  }

  @Test
  fun `concurrent access with different keys works correctly`() = runBlocking(Dispatchers.Default) {
    val cache = AsyncCache<Int, String>(this)

    val deferreds = (1..100).map { key ->
      async {
        cache.getOrPut(key) {
          delay(10)
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

  @Test
  fun `failed computation with concurrent waiters - all see exception`() {
    val loadCount = AtomicInteger(0)
    val cache = AsyncCache<String, String>(CoroutineScope(Dispatchers.Default))

    // Launch multiple concurrent requests that will all fail
    runBlocking {
      val deferreds = (1..5).map {
        async {
          val exception = Assertions.assertThrows(IllegalStateException::class.java) {
            runBlocking {
              cache.getOrPut("failing-key") {
                val count = loadCount.incrementAndGet()
                delay(20)
                throw IllegalStateException("Failed attempt $count")
              }
            }
          }

          assertThat(exception.message).startsWith("Failed attempt")
          "caught-exception"
        }
      }

      val results = deferreds.awaitAll()

      // All concurrent requests should see the exception
      assertThat(results).allMatch { it == "caught-exception" }
      // Only one should have tried (they shared the computation)
      assertThat(loadCount.get()).isEqualTo(1)
    }

    // Next call ALSO gets cached failure - no retry
    val exception = Assertions.assertThrows(IllegalStateException::class.java) {
      runBlocking {
        cache.getOrPut("failing-key") {
          loadCount.incrementAndGet()
          "should-not-be-called"
        }
      }
    }

    assertThat(exception.message).startsWith("Failed attempt")
    assertThat(loadCount.get()).isEqualTo(1) // Still 1 - failure is cached
  }

  @Test
  fun `cache works with custom scope`(): Unit = runBlocking(Dispatchers.Default) {
    val customScope = CoroutineScope(coroutineContext)
    val cache = AsyncCache<String, String>(customScope)

    val result = cache.getOrPut("key") { "value" }

    assertThat(result).isEqualTo("value")
  }

  @Test
  fun `rapid sequential access uses cache`(): Unit = runBlocking(Dispatchers.Default) {
    val loadCount = AtomicInteger(0)
    val cache = AsyncCache<String, Int>(this)

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
  fun `complex value types are cached correctly`(): Unit = runBlocking(Dispatchers.Default) {
    val cache = AsyncCache<String, List<String>>(this)
    val expected = listOf("a", "b", "c")

    val result1 = cache.getOrPut("complex") { expected }
    val result2 = cache.getOrPut("complex") { listOf("x", "y", "z") }

    assertThat(result1).isEqualTo(expected)
    assertThat(result2).isSameAs(result1) // Should be the exact same cached instance
  }
}
