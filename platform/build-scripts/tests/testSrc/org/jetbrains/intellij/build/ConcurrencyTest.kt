// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import com.intellij.platform.buildScripts.concurrency.TaskFailedException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class ConcurrencyTest {
  @Test
  fun forEachConcurrentRunsEveryItemOnAVirtualThreadWithinTheConcurrencyLimit() {
    val running = AtomicInteger()
    val maxRunning = AtomicInteger()
    val seen = ConcurrentHashMap.newKeySet<Int>()
    val virtual = ConcurrentHashMap.newKeySet<Boolean>()

    (1..20).toList().forEachConcurrent(concurrency = 3) { item ->
      val now = running.incrementAndGet()
      maxRunning.accumulateAndGet(now, ::maxOf)
      virtual.add(Thread.currentThread().isVirtual)
      Thread.sleep(20)
      seen.add(item)
      running.decrementAndGet()
    }

    assertThat(seen).containsExactlyInAnyOrderElementsOf(1..20)
    assertThat(maxRunning.get()).isBetween(2, 3)
    assertThat(virtual).containsExactly(true)
  }

  @Test
  fun mapConcurrentPreservesInputOrder() {
    val result = listOf(1, 2, 3).mapConcurrent(concurrency = 3) { value ->
      Thread.sleep(((4 - value) * 10).toLong())
      value
    }

    assertThat(result).containsExactly(1, 2, 3)
  }

  @Test
  fun mapConcurrentHandlesAnEmptyCollectionAndASingleItem() {
    assertThat(emptyList<Int>().mapConcurrent { it }).isEmpty()
    assertThat(setOf(7).mapConcurrent(concurrency = 1) { it * 2 }).containsExactly(14)
  }

  @Test
  fun mapConcurrentValidatesConcurrency() {
    assertThatThrownBy {
      listOf(1).mapConcurrent(concurrency = 0) { it }
    }
      .isInstanceOf(IllegalArgumentException::class.java)
      .hasMessageContaining("Concurrency must be positive")
  }

  @Test
  fun mapConcurrentPropagatesTheFirstFailureAndInterruptsTheOtherWorkers() {
    val siblingInterrupted = CompletableFuture<Unit>()
    val siblingStarted = CountDownLatch(1)
    assertThatThrownBy {
      listOf(1, 2).mapConcurrent(concurrency = 2) { item ->
        if (item == 1) {
          siblingStarted.countDown()
          try {
            Thread.sleep(10_000)
          }
          catch (e: InterruptedException) {
            siblingInterrupted.complete(Unit)
            throw e
          }
        }
        siblingStarted.await()
        check(item != 2) { "boom" }
        item
      }
    }
      .isInstanceOf(TaskFailedException::class.java)
      .hasMessageContaining("boom")

    assertThat(siblingInterrupted.orTimeout(5, TimeUnit.SECONDS).join()).isEqualTo(Unit)
  }

  @Test
  fun mapConcurrentPropagatesACancellationThrownByAnAction() {
    assertThatThrownBy {
      listOf(1, 2, 3).mapConcurrent(concurrency = 2) { item ->
        if (item == 2) {
          throw CancellationException("cancel")
        }
        Thread.sleep(50)
        item
      }
    }
      .isInstanceOf(TaskFailedException::class.java)
      .hasCauseInstanceOf(CancellationException::class.java)
  }
}
