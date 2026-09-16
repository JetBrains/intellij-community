// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import com.dynatrace.hash4j.hashing.Hashing
import com.intellij.platform.buildScripts.concurrency.taskScope
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@Timeout(30)
@Suppress("ReplacePutWithAssignment")
internal class StripedLockTest {
  @Test
  fun `the stripe count must be a positive power of two`() {
    assertThatThrownBy { StripedLock(0) }.isInstanceOf(IllegalArgumentException::class.java)
    assertThatThrownBy { StripedLock(3) }.isInstanceOf(IllegalArgumentException::class.java)
    for (count in listOf(1, 2, 4, 8, 16)) {
      assertThat(StripedLock(count).withLock("key") { count }).isEqualTo(count)
    }
  }

  @Test
  fun `a string key and its hash use the same stripe`() {
    val striped = StripedLock(16)
    val key = "test-key"
    val hash = Hashing.xxh3_64().hashBytesToLong(key.toByteArray())
    striped.withLock(key) {
      assertInterruptedWaiter { action -> striped.withLockByHash(hash, action) }
    }
  }

  @Test
  fun `hash stripes ignore high bits`() {
    val striped = StripedLock(8)
    for (hash in 0L until 8L) {
      striped.withLockByHash(hash) {
        assertInterruptedWaiter { action -> striped.withLockByHash(hash + 8, action) }
        assertInterruptedWaiter { action -> striped.withLockByHash(hash or Long.MIN_VALUE, action) }
      }
    }
  }

  @Test
  fun `a cancelled string waiter never enters its action`() {
    val striped = StripedLock(8)
    striped.withLock("key") {
      assertInterruptedWaiter { action -> striped.withLock("key", action) }
    }
  }

  @Test
  fun `an interrupted caller does not enter an available stripe`() {
    val striped = StripedLock()
    try {
      Thread.currentThread().interrupt()
      assertThatThrownBy { striped.withLock("key") { error("the action must not run") } }
        .isInstanceOf(InterruptedException::class.java)
    }
    finally {
      Thread.interrupted()
    }
  }

  @Test
  fun `an action can acquire its stripe again`() {
    val striped = StripedLock(1)
    assertThat(striped.withLock("key") { striped.withLockByHash(42) { "result" } }).isEqualTo("result")
  }

  @Test
  fun `different stripes run independently`() {
    val striped = StripedLock(8)
    striped.withLockByHash(0) {
      val result = CompletableFuture<Int>()
      val worker = Thread.ofVirtual().start { result.complete(striped.withLockByHash(1) { 42 }) }
      try {
        assertThat(result.get(5, TimeUnit.SECONDS)).isEqualTo(42)
      }
      finally {
        worker.interrupt()
        worker.join(5000)
        assertThat(worker.isAlive).isFalse()
      }
    }
  }

  @Test
  fun `a failed action releases its stripe`() {
    val striped = StripedLock(1)
    val failure = IllegalStateException("the action failed")
    assertThatThrownBy { striped.withLock("key") { throw failure } }.isSameAs(failure)
    val result = CompletableFuture<Int>()
    val worker = Thread.ofVirtual().start { result.complete(striped.withLock("key") { 42 }) }
    try {
      assertThat(result.get(5, TimeUnit.SECONDS)).isEqualTo(42)
    }
    finally {
      worker.interrupt()
      worker.join(5000)
      assertThat(worker.isAlive).isFalse()
    }
  }

  @Test
  fun `concurrent actions serialize updates for each key`() {
    val striped = StripedLock(8)
    val counters = ConcurrentHashMap<String, Int>()
    val keys = listOf("one", "two", "three", "four")
    keys.forEach { counters.put(it, 0) }
    taskScope {
      for (key in keys) {
        repeat(10) { worker ->
          fork("$key worker $worker") {
            repeat(100) {
              striped.withLock(key) {
                val previous = counters.getValue(key)
                Thread.yield()
                counters.put(key, previous + 1)
              }
            }
          }
        }
      }
      join()
    }
    assertThat(counters.values).containsOnly(1000)
  }

  private fun assertInterruptedWaiter(withLock: (() -> Unit) -> Unit) {
    val started = CountDownLatch(1)
    val entered = AtomicBoolean()
    val result = CompletableFuture<Throwable?>()
    val waiter = Thread.ofVirtual().start {
      try {
        started.countDown()
        withLock { entered.set(true) }
        result.complete(null)
      }
      catch (failure: Throwable) {
        result.complete(failure)
      }
    }
    try {
      assertThat(started.await(5, TimeUnit.SECONDS)).isTrue()
      val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
      while (waiter.state != Thread.State.WAITING && !result.isDone && System.nanoTime() < deadline) {
        Thread.sleep(1)
      }
      assertThat(waiter.state).isEqualTo(Thread.State.WAITING)
      waiter.interrupt()
      assertThat(result.get(5, TimeUnit.SECONDS)).isInstanceOf(InterruptedException::class.java)
      assertThat(entered.get()).isFalse()
    }
    finally {
      waiter.interrupt()
      waiter.join(5000)
      assertThat(waiter.isAlive).isFalse()
    }
  }
}
