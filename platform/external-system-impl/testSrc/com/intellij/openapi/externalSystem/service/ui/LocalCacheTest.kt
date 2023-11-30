// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.ui

import com.intellij.openapi.externalSystem.service.ui.completion.cache.AsyncLocalCache
import com.intellij.openapi.externalSystem.service.ui.completion.cache.BasicLocalCache
import kotlinx.coroutines.*
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

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
  fun `test async local cache`() {
    runBlocking {
      val cache = AsyncLocalCache<String>()
      Assertions.assertNull(cache.getValue())
      Assertions.assertEquals("text 1", cache.getOrCreateValue(1) { "text 1" })
      Assertions.assertEquals("text 1", cache.getValue())
      Assertions.assertEquals("text 2", cache.getOrCreateValue(2) { "text 2" })
      Assertions.assertEquals("text 2", cache.getValue())
      Assertions.assertEquals("text 2", cache.getOrCreateValue(0) { "text 3" })
      Assertions.assertEquals("text 2", cache.getValue())
    }
  }

  @Test
  fun `test basic local cache concurrency`() {
    val attemptNum = 1_000
    val threadNum = maxOf(Runtime.getRuntime().availableProcessors(), 10)
    repeat(attemptNum) {
      val cache = BasicLocalCache<Int>()
      val latch = CountDownLatch(1)
      val counter = AtomicInteger(0)
      val threads = (0 until threadNum).map {
        thread {
          latch.await()
          val stamp = counter.incrementAndGet()
          cache.getOrCreateValue(stamp.toLong()) { stamp }
        }
      }
      latch.countDown()
      threads.forEach { it.join() }
      Assertions.assertEquals(threadNum, cache.getValue())
    }
  }

  @Test
  fun `test async local cache concurrency`() {
    val attemptNum = 1_000
    val coroutineNum = 1_000
    repeat(attemptNum) {
      runBlocking {
        val cache = AsyncLocalCache<Int>()
        val counter = AtomicInteger(0)
        withContext(Dispatchers.Default) {
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
  }

  @Test
  fun `test async local cache concurrent init`() {
    val initValue = "text"
    val attemptNum = 1_000
    val coroutineNum = 1_000
    repeat(attemptNum) {
      runBlocking {
        val cache = AsyncLocalCache<String>()
        val counter = AtomicInteger(0)
        withContext(Dispatchers.Default) {
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
  }
}