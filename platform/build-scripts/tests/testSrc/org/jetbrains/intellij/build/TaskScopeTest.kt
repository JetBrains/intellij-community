// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import io.opentelemetry.context.Context
import io.opentelemetry.context.ContextKey
import io.opentelemetry.extension.kotlin.asContextElement
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class TaskScopeTest {
  @Test
  fun `a fork runs on a virtual thread and returns its value`() {
    val (isVirtual, threadName) = runBlocking {
      taskScope {
        fork("worker") { Thread.currentThread().isVirtual to Thread.currentThread().name }.await()
      }
    }

    assertThat(isVirtual).isTrue()
    assertThat(threadName).startsWith("build-")
  }

  @Test
  fun `the group waits for a fork that nobody awaits`() {
    val done = CompletableFuture<Unit>()
    runBlocking {
      taskScope {
        fork("late") {
          delay(100.milliseconds)
          done.complete(Unit)
        }
      }
    }

    assertThat(done).isCompleted
  }

  @Test
  fun `fail fast cancels the other forks and rethrows the first failure`() {
    val siblingCancelled = CompletableFuture<Unit>()
    assertThatThrownBy {
      runBlocking {
        taskScope {
          fork("sibling") {
            try {
              awaitCancellation()
            }
            catch (e: CancellationException) {
              siblingCancelled.complete(Unit)
              throw e
            }
          }
          fork("failing") {
            delay(50.milliseconds)
            throw IllegalStateException("the fork failed")
          }
        }
      }
    }
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessage("the fork failed")

    assertThat(siblingCancelled.orTimeout(5, TimeUnit.SECONDS).join()).isEqualTo(Unit)
  }

  /** The block awaits a fork that the group cancels because a sibling failed. The failure is thrown, not the cancellation. */
  @Test
  fun `a failure of a fork that the block does not await wins over the cancellation the block sees`() {
    assertThatThrownBy {
      runBlocking {
        taskScope {
          val slow = fork("slow") { awaitCancellation() }
          fork("failing") {
            delay(50.milliseconds)
            throw IllegalStateException("the fork failed")
          }
          slow.await()
        }
      }
    }
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessage("the fork failed")
  }

  @Test
  fun `a second failure is attached as suppressed under fail fast`() {
    assertThatThrownBy {
      runBlocking {
        taskScope {
          fork("first") { throw IllegalStateException("first") }
          fork("second") {
            // a blocking body does not see the cancel, so its own failure is kept
            Thread.sleep(100)
            throw IllegalArgumentException("second")
          }
        }
      }
    }
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessage("first")
      .satisfies({ e ->
        assertThat(e.suppressed).hasSize(1)
        assertThat(e.suppressed[0]).isInstanceOf(IllegalArgumentException::class.java).hasMessage("second")
      })
  }

  @Test
  fun `a failure of the block carries the failure of a fork as suppressed`() {
    val forkFailed = CompletableFuture<Unit>()
    assertThatThrownBy {
      runBlocking {
        taskScope(TaskScopePolicy.RUN_ALL) {
          fork("failing") {
            try {
              throw IllegalStateException("the fork failed")
            }
            finally {
              forkFailed.complete(Unit)
            }
          }
          forkFailed.await()
          throw IllegalArgumentException("the block failed")
        }
      }
    }
      .isInstanceOf(IllegalArgumentException::class.java)
      .hasMessage("the block failed")
      .satisfies({ e ->
        assertThat(e.suppressed).hasSize(1)
        assertThat(e.suppressed[0]).hasMessage("the fork failed")
      })
  }

  /** A fork that a cancelled fork starts must not outlive the cancel. A fork cancelled before its start never runs. */
  @Test
  fun `a fork started after the group was cancelled is cancelled`() {
    val lateForkRan = AtomicBoolean()
    assertThatThrownBy {
      runBlocking {
        taskScope {
          val group = this
          fork("starter") {
            try {
              awaitCancellation()
            }
            catch (e: CancellationException) {
              group.fork("late") {
                delay(500.milliseconds)
                lateForkRan.set(true)
              }
              throw e
            }
          }
          fork("failing") {
            delay(50.milliseconds)
            throw IllegalStateException("the fork failed")
          }
        }
      }
    }.isInstanceOf(IllegalStateException::class.java)

    assertThat(lateForkRan.get()).isFalse()
  }

  /** A blocking body does not see the cancellation, so the group must wait for it and not for the cancelled future. */
  @Test
  fun `the group waits for the body of a cancelled fork to end`() {
    val slowEnded = AtomicBoolean()
    assertThatThrownBy {
      runBlocking {
        taskScope {
          fork("slow") {
            Thread.sleep(300)
            slowEnded.set(true)
          }
          fork("failing") { throw IllegalStateException("the fork failed") }
        }
      }
    }.isInstanceOf(IllegalStateException::class.java)

    assertThat(slowEnded.get()).isTrue()
  }

  @Test
  fun `run all lets every fork finish and reports every failure`() {
    val slowFinished = CompletableFuture<Unit>()
    assertThatThrownBy {
      runBlocking {
        taskScope(TaskScopePolicy.RUN_ALL) {
          fork("failing") { throw IllegalStateException("first") }
          fork("slow") {
            delay(200.milliseconds)
            slowFinished.complete(Unit)
          }
          fork("failing too") {
            delay(50.milliseconds)
            throw IllegalArgumentException("second")
          }
        }
      }
    }
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessage("first")
      .satisfies({ e ->
        assertThat(e.suppressed).hasSize(1)
        assertThat(e.suppressed[0]).hasMessage("second")
      })

    assertThat(slowFinished).isCompleted
  }

  @Test
  fun `a cancelled awaiter cancels the forks`() {
    val forkCancelled = CompletableFuture<Unit>()
    runBlocking {
      val job = launch {
        taskScope {
          fork("endless") {
            try {
              awaitCancellation()
            }
            catch (e: CancellationException) {
              forkCancelled.complete(Unit)
              throw e
            }
          }
        }
      }
      delay(100.milliseconds)
      withTimeout(5.seconds) {
        job.cancelAndJoin()
      }
    }

    assertThat(forkCancelled.orTimeout(5, TimeUnit.SECONDS).join()).isEqualTo(Unit)
  }

  /** Only the group cancels a fork. An awaiter that is cancelled stops waiting and changes nothing. */
  @Test
  fun `a cancelled awaiter leaves the fork running`() {
    val forkReleased = CompletableFuture<Unit>()
    val forkEnded = AtomicBoolean()
    val forkResult = runBlocking {
      val callerScope = this
      taskScope {
        val fork = fork("shared") {
          forkReleased.await()
          forkEnded.set(true)
          "done"
        }
        val awaiter = callerScope.launch {
          fork.await()
        }
        delay(50.milliseconds)
        awaiter.cancelAndJoin()
        assertThat(forkEnded.get()).isFalse()
        forkReleased.complete(Unit)
        fork.await()
      }
    }

    assertThat(forkResult).isEqualTo("done")
  }

  /** The span helpers install the telemetry context as a coroutine context element, and a fork inherits that element. */
  @Test
  fun `a fork sees the telemetry context of the caller`() {
    val key = ContextKey.named<String>("TaskScopeTest")
    val seen = runBlocking {
      withContext(Context.current().with(key, "from the caller").asContextElement()) {
        taskScope {
          fork("reader") { Context.current().get(key) }.await()
        }
      }
    }

    assertThat(seen).isEqualTo("from the caller")
  }

  @Test
  fun `the coroutines of a fork body run on more than one thread`() {
    val threads = ConcurrentHashMap.newKeySet<String>()
    runBlocking {
      taskScope {
        fork("parent") {
          repeat(8) {
            launch {
              Thread.sleep(50)
              threads.add(Thread.currentThread().name)
            }
          }
        }.await()
      }
    }

    assertThat(threads).hasSizeGreaterThan(1)
    assertThat(threads).allSatisfy { assertThat(it).startsWith("build-") }
  }
}
