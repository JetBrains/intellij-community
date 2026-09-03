// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds

class ConcurrencyTest {
  @Test
  fun forEachConcurrentRunsEveryItemOnAVirtualThreadWithinTheConcurrencyLimit() {
    val running = AtomicInteger()
    val maxRunning = AtomicInteger()
    val seen = ConcurrentHashMap.newKeySet<Int>()
    val virtual = ConcurrentHashMap.newKeySet<Boolean>()

    runBlocking {
      (1..20).toList().forEachConcurrent(concurrency = 3) { item ->
        val now = running.incrementAndGet()
        maxRunning.accumulateAndGet(now, ::maxOf)
        virtual.add(Thread.currentThread().isVirtual)
        Thread.sleep(20)
        seen.add(item)
        running.decrementAndGet()
      }
    }

    assertThat(seen).containsExactlyInAnyOrderElementsOf(1..20)
    assertThat(maxRunning.get()).isBetween(2, 3)
    assertThat(virtual).containsExactly(true)
  }

  @Test
  fun mapConcurrentPreservesInputOrder() {
    val result = runBlocking {
      listOf(1, 2, 3).mapConcurrent(concurrency = 3) { value ->
        delay(((4 - value) * 10).milliseconds)
        value
      }
    }

    assertThat(result).containsExactly(1, 2, 3)
  }

  @Test
  fun mapConcurrentHandlesAnEmptyCollectionAndASingleItem() {
    runBlocking {
      assertThat(emptyList<Int>().mapConcurrent { it }).isEmpty()
      assertThat(setOf(7).mapConcurrent(concurrency = 1) { it * 2 }).containsExactly(14)
    }
  }

  @Test
  fun mapConcurrentValidatesConcurrency() {
    assertThatThrownBy {
      runBlocking {
        listOf(1).mapConcurrent(concurrency = 0) { it }
      }
    }
      .isInstanceOf(IllegalArgumentException::class.java)
      .hasMessageContaining("Concurrency must be positive")
  }

  @Test
  fun mapConcurrentPropagatesTheFirstFailureAndCancelsTheOtherWorkers() {
    val siblingCancelled = CompletableFuture<Unit>()
    assertThatThrownBy {
      runBlocking {
        listOf(1, 2).mapConcurrent(concurrency = 2) { item ->
          if (item == 1) {
            try {
              awaitCancellation()
            }
            catch (e: CancellationException) {
              siblingCancelled.complete(Unit)
              throw e
            }
          }
          check(item != 2) { "boom" }
          item
        }
      }
    }
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessageContaining("boom")

    assertThat(siblingCancelled.orTimeout(5, TimeUnit.SECONDS).join()).isEqualTo(Unit)
  }

  @Test
  fun mapConcurrentPropagatesCancellation() {
    assertThatThrownBy {
      runBlocking {
        listOf(1, 2, 3).mapConcurrent(concurrency = 2) { item ->
          if (item == 2) {
            throw CancellationException("cancel")
          }
          delay(50.milliseconds)
          item
        }
      }
    }
      .isInstanceOf(CancellationException::class.java)
      .satisfies({ e -> assertThat(generateSequence(e) { it.cause }.map { it.message }.toList()).contains("cancel") })
  }
}
