// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import com.intellij.platform.buildScripts.concurrency.Awaitable
import com.intellij.platform.buildScripts.concurrency.Joiner
import com.intellij.platform.buildScripts.concurrency.Subtask
import com.intellij.platform.buildScripts.concurrency.TaskFailedException
import com.intellij.platform.buildScripts.concurrency.TaskScope
import com.intellij.platform.buildScripts.concurrency.currentSingleFlightOwners
import com.intellij.platform.buildScripts.concurrency.taskScope
import com.intellij.platform.buildScripts.concurrency.withSingleFlightOwners
import com.intellij.util.ref.GCUtil
import io.opentelemetry.context.Context
import io.opentelemetry.context.ContextKey
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.lang.ref.WeakReference
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.milliseconds

@Timeout(20)
class TaskScopeTest {
  @Test
  fun `named virtual threads read dependencies before join and results after join`() {
    val result = taskScope {
      val first = fork("first") { 21 }
      val second = fork("second") {
        assertThat(Thread.currentThread().isVirtual).isTrue()
        assertThat(Thread.currentThread().name).isEqualTo("second")
        first.await() * 2
      }
      assertThatThrownBy { first.get() }.isInstanceOf(IllegalStateException::class.java)
      join { second.get() }
    }
    assertThat(result).isEqualTo(42)
    assertThat(Awaitable.completed(42).await()).isEqualTo(42)
  }

  @Test
  fun `close cancels unjoined work and reports the missing join`() {
    val entered = CountDownLatch(1)
    val finished = AtomicBoolean()
    assertThatThrownBy {
      taskScope {
        fork("worker") {
          entered.countDown()
          try {
            CountDownLatch(1).await()
          }
          finally {
            finished.set(true)
          }
        }
        entered.await()
      }
    }.isInstanceOf(IllegalStateException::class.java).hasMessageContaining("without a join")
    assertThat(finished.get()).isTrue()
  }

  @Test
  fun `body failure stays primary and close skips the missing-join report`() {
    val failure = IllegalArgumentException("body failed")
    assertThatThrownBy {
      taskScope {
        fork("worker") { CountDownLatch(1).await() }
        throw failure
      }
    }.isSameAs(failure)
    assertThat(failure.suppressed).isEmpty()
  }

  @Test
  fun `fail fast cancels nested scopes and waits for cleanup`() {
    val entered = CountDownLatch(1)
    val cleaned = AtomicBoolean()
    val failure = IllegalStateException("failure")
    assertThatThrownBy {
      taskScope {
        fork("outer") {
          taskScope {
            fork("inner") {
              entered.countDown()
              try {
                CountDownLatch(1).await()
              }
              finally {
                cleaned.set(true)
              }
            }
            join()
          }
        }
        fork("failure") { entered.await(); throw failure }
        join()
      }
    }.isInstanceOf(TaskFailedException::class.java).hasCause(failure)
    assertThat(cleaned.get()).isTrue()
  }

  @Test
  fun `worker cancellation and interruption are task failures`() {
    for (failure in listOf(CancellationException("cancelled"), InterruptedException("interrupted"))) {
      assertThatThrownBy {
        taskScope { fork("failure") { throw failure }; join() }
      }.isInstanceOf(TaskFailedException::class.java).hasCause(failure)
      assertThat(Thread.currentThread().isInterrupted).isFalse()
    }
  }

  @Test
  fun `await all exposes outcomes without cancelling siblings`() {
    val failure = IllegalStateException("failure")
    taskScope(joiner = Joiner.awaitAll()) {
      val failed = fork("failed") { throw failure }
      val success = fork("success") { 42 }
      join {
        assertThat(failed.state()).isEqualTo(Subtask.State.FAILED)
        assertThat(failed.exception()).isSameAs(failure)
        assertThat(success.get()).isEqualTo(42)
      }
    }
  }

  @Test
  fun `await all then throw reports each failure`() {
    val first = IllegalStateException("first")
    val second = IllegalArgumentException("second")
    val success = AtomicBoolean()
    assertThatThrownBy {
      taskScope(joiner = Joiner.awaitAllOrThrow()) {
        val task = fork("first") { throw first }
        assertThatThrownBy { task.await() }.hasCause(first)
        fork("second") { throw second }
        fork("success") { success.set(true) }
        join()
      }
    }.hasCause(first).satisfies({ error -> assertThat(error.suppressed).containsExactly(second) })
    assertThat(success.get()).isTrue()
  }

  @Test
  fun `only the owner manages the scope and joins once`() {
    taskScope {
      val scope = this
      fork("non-owner") {
        assertThatThrownBy { scope.fork("illegal") {} }.hasMessageContaining("Only the owner")
        assertThatThrownBy { scope.join() }.hasMessageContaining("Only the owner")
        assertThatThrownBy { scope.close() }.hasMessageContaining("Only the owner")
      }
      join()
      assertThatThrownBy { fork("after join") {} }.isInstanceOf(IllegalStateException::class.java)
      assertThatThrownBy { join() }.isInstanceOf(IllegalStateException::class.java)
    }
    TaskScope.open().use { outer ->
      TaskScope.open().use { inner ->
        assertThatThrownBy { outer.close() }.hasMessageContaining("Nested task scopes")
        inner.join()
      }
      outer.join()
    }
  }

  @Test
  fun `a factory failure cancels workers that already started`() {
    val entered = CountDownLatch(1)
    val cleaned = AtomicBoolean()
    val failure = IllegalStateException("cannot start")
    var count = 0
    val factory = ThreadFactory { runnable ->
      if (count++ == 0) Thread.ofVirtual().unstarted(runnable) else throw failure
    }
    assertThatThrownBy {
      TaskScope.open(threadFactory = factory).use { scope ->
        scope.fork("first") {
          entered.countDown()
          try {
            CountDownLatch(1).await()
          }
          finally {
            cleaned.set(true)
          }
        }
        entered.await()
        scope.fork("cannot start") {}
      }
    }.isSameAs(failure)
    assertThat(cleaned.get()).isTrue()
  }

  @Test
  fun `deadline cancels workers`() {
    val entered = CountDownLatch(1)
    val cleaned = AtomicBoolean()
    assertThatThrownBy {
      taskScope(timeout = 200.milliseconds) {
        fork("worker") {
          entered.countDown()
          try {
            CountDownLatch(1).await()
          }
          finally {
            cleaned.set(true)
          }
        }
        entered.await()
        join()
      }
    }.isInstanceOf(TimeoutException::class.java)
    assertThat(cleaned.get()).isTrue()
  }

  @Test
  fun `close waits for actual thread termination after result publication`() {
    val published = CountDownLatch(1)
    val release = CountDownLatch(1)
    val finished = CompletableFuture<Unit>()
    val factory = ThreadFactory { runnable ->
      Thread.ofVirtual().unstarted { runnable.run(); published.countDown(); awaitUninterruptibly(release) }
    }
    val owner = Thread.ofVirtual().start {
      try {
        TaskScope.open(threadFactory = factory).use { scope ->
          val task = scope.fork("worker") { 42 }
          scope.join { assertThat(task.get()).isEqualTo(42) }
          published.await()
        }
        finished.complete(Unit)
      }
      catch (error: Throwable) {
        finished.completeExceptionally(error)
      }
    }
    try {
      assertThat(published.await(5, TimeUnit.SECONDS)).isTrue()
      assertThat(finished.isDone).isFalse()
    }
    finally {
      release.countDown(); owner.join(5000)
    }
    assertThat(finished.get(5, TimeUnit.SECONDS)).isEqualTo(Unit)
  }

  @Test
  fun `repeated interruption during close preserves the flag and waits for cleanup`() {
    val entered = CountDownLatch(1)
    val cleaning = CountDownLatch(1)
    val release = CountDownLatch(1)
    val result = CompletableFuture<Boolean>()
    val owner = Thread.ofVirtual().start {
      try {
        taskScope {
          fork("worker") {
            entered.countDown()
            try {
              CountDownLatch(1).await()
            }
            finally {
              cleaning.countDown(); awaitUninterruptibly(release)
            }
          }
          join()
        }
      }
      catch (_: InterruptedException) {
        result.complete(Thread.currentThread().isInterrupted)
      }
      catch (error: Throwable) {
        result.completeExceptionally(error)
      }
    }
    try {
      assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue()
      owner.interrupt()
      assertThat(cleaning.await(5, TimeUnit.SECONDS)).isTrue()
      repeat(10) { owner.interrupt() }
      assertThat(result.isDone).isFalse()
    }
    finally {
      release.countDown(); owner.join(5000)
    }
    assertThat(result.get(5, TimeUnit.SECONDS)).isTrue()
  }

  @Test
  fun `forks inherit telemetry and single flight ownership`() {
    val key = ContextKey.named<String>("TaskScopeTest")
    val token = Any()
    val result = Context.current().with(key, "parent").makeCurrent().use {
      withSingleFlightOwners(emptySet(), token) {
        taskScope {
          val task = fork("reader") { Context.current().get(key) to currentSingleFlightOwners() }
          join { task.get() }
        }
      }
    }
    assertThat(result.first).isEqualTo("parent")
    assertThat(result.second).containsExactly(token)
  }

  @Test
  fun `a terminated thread releases captured work`() {
    val worker = AtomicReference<Thread>()
    val payload = runWithPayload(worker)
    GCUtil.tryGcSoftlyReachableObjects()
    assertThat(worker.get().name).isEqualTo("holder")
    assertThat(payload.get()).isNull()
  }

  private fun runWithPayload(worker: AtomicReference<Thread>): WeakReference<Any> {
    val payload = Any()
    taskScope { fork("holder") { worker.set(Thread.currentThread()); payload.hashCode() }; join() }
    return WeakReference(payload)
  }

  private fun awaitUninterruptibly(latch: CountDownLatch) {
    while (true) {
      try {
        check(latch.await(5, TimeUnit.SECONDS)); return
      }
      catch (_: InterruptedException) {
      }
    }
  }
}
