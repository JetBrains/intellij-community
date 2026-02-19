// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.ui

import com.intellij.openapi.externalSystem.service.ui.completion.cache.AsyncLocalCache
import com.intellij.openapi.externalSystem.service.ui.completion.cache.BasicLocalCache
import com.intellij.openapi.externalSystem.util.Parallel.Companion.parallel
import com.intellij.testFramework.common.timeoutRunBlocking
import kotlinx.coroutines.launch
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

class LocalCacheTest {

  @Test
  fun `test basic local cache`() {
    val cache = BasicLocalCache<String>()
    Assertions.assertNull(cache.getValue())
    Assertions.assertEquals("text 1", cache.getOrCreateValue(1) { "text 1" })
    Assertions.assertEquals("text 1", cache.getValue())
    Assertions.assertEquals("text 2", cache.getOrCreateValue(2) { "text 2" })
    Assertions.assertEquals("text 2", cache.getValue())
    Assertions.assertEquals("text 2", cache.getOrCreateValue(0) { "text 3" })
    Assertions.assertEquals("text 2", cache.getValue())
  }

  @Test
  fun `test async local cache`() = timeoutRunBlocking {
      val cache = AsyncLocalCache<String>()
      Assertions.assertNull(cache.getValue())
      Assertions.assertEquals("text 1", cache.getOrCreateValue(1) { "text 1" })
      Assertions.assertEquals("text 1", cache.getValue())
      Assertions.assertEquals("text 2", cache.getOrCreateValue(2) { "text 2" })
      Assertions.assertEquals("text 2", cache.getValue())
      Assertions.assertEquals("text 2", cache.getOrCreateValue(0) { "text 3" })
      Assertions.assertEquals("text 2", cache.getValue())
    }

  @Test
  fun `test blocking local cache`() {
    val cache = AsyncLocalCache<String>()
    Assertions.assertNull(cache.getValue())
    Assertions.assertEquals("text 1", cache.getOrCreateValueBlocking(1) { "text 1" })
    Assertions.assertEquals("text 1", cache.getValue())
    Assertions.assertEquals("text 2", cache.getOrCreateValueBlocking(2) { "text 2" })
    Assertions.assertEquals("text 2", cache.getValue())
    Assertions.assertEquals("text 2", cache.getOrCreateValueBlocking(0) { "text 3" })
    Assertions.assertEquals("text 2", cache.getValue())
  }

  @Test
  fun `test basic local cache concurrency`() {
    val attemptNum = 1_000
    val threadNum = maxOf(Runtime.getRuntime().availableProcessors(), 10)
    repeat(attemptNum) {
      val cache = BasicLocalCache<Int>()
      val counter = AtomicInteger(0)
      parallel {
        repeat(threadNum) {
          thread {
            val stamp = counter.incrementAndGet()
            cache.getOrCreateValue(stamp.toLong()) { stamp }
          }
        }
      }
      Assertions.assertEquals(threadNum, cache.getValue())
    }
  }

  @Test
  fun `test async local cache concurrency`() {
    val attemptNum = 1_000
    val coroutineNum = 1_000
    repeat(attemptNum) {
      val cache = AsyncLocalCache<Int>()
      val counter = AtomicInteger(0)
      timeoutRunBlocking {
        repeat(coroutineNum) {
          launch {
            val stamp = counter.incrementAndGet()
            cache.getOrCreateValue(stamp.toLong()) { stamp }
          }
        }
      }
      Assertions.assertEquals(coroutineNum, cache.getValue())
    }
  }

  @Test
  fun `test blocking local cache concurrency`() {
    val attemptNum = 1_000
    val threadNum = maxOf(Runtime.getRuntime().availableProcessors(), 10)
    repeat(attemptNum) {
      val cache = AsyncLocalCache<Int>()
      val counter = AtomicInteger(0)
      parallel {
        repeat(threadNum) {
          thread {
            val stamp = counter.incrementAndGet()
            cache.getOrCreateValueBlocking(stamp.toLong()) { stamp }
          }
        }
      }
      Assertions.assertEquals(threadNum, cache.getValue())
    }
  }

  @Test
  fun `test async local cache concurrent init`() {
    val initValue = "text"
    val attemptNum = 1_000
    val coroutineNum = 1_000
    repeat(attemptNum) {
      val cache = AsyncLocalCache<String>()
      val counter = AtomicInteger(0)
      timeoutRunBlocking {
        repeat(coroutineNum) {
          launch {
            Assertions.assertEquals(initValue, cache.getOrCreateValue(0) {
              counter.incrementAndGet()
              return@getOrCreateValue initValue
            })
          }
        }
      }
      Assertions.assertEquals(1, counter.get())
      Assertions.assertEquals(initValue, cache.getValue())
    }
  }

  @Test
  fun `test blocking local cache concurrent init`() {
    val initValue = "text"
    val attemptNum = 1_000
    val threadNum = maxOf(Runtime.getRuntime().availableProcessors(), 10)
    repeat(attemptNum) {
      val cache = AsyncLocalCache<String>()
      val counter = AtomicInteger(0)
      parallel {
        repeat(threadNum) {
          thread {
            Assertions.assertEquals(initValue, cache.getOrCreateValueBlocking(0) {
              counter.incrementAndGet()
              return@getOrCreateValueBlocking initValue
            })
          }
        }
      }
      Assertions.assertEquals(1, counter.get())
      Assertions.assertEquals(initValue, cache.getValue())
    }
  }
}