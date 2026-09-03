// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import io.opentelemetry.context.Context
import io.opentelemetry.context.ContextKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class VirtualThreadTasksTest {
  @Test
  fun `a fork runs on a virtual thread and returns its value`() {
    val (isVirtual, threadName) = runBlocking {
      virtualThreadTasks {
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
      virtualThreadTasks {
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
        virtualThreadTasks {
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

    assertThat(siblingCancelled.orTimeout(5, java.util.concurrent.TimeUnit.SECONDS).join()).isEqualTo(Unit)
  }

  @Test
  fun `run all lets every fork finish and reports every failure`() {
    val slowFinished = CompletableFuture<Unit>()
    assertThatThrownBy {
      runBlocking {
        virtualThreadTasks(VirtualThreadTaskPolicy.RUN_ALL) {
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
        virtualThreadTasks {
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

    assertThat(forkCancelled.orTimeout(5, java.util.concurrent.TimeUnit.SECONDS).join()).isEqualTo(Unit)
  }

  @Test
  fun `a cancelled awaiter of awaitShared leaves the fork running`() {
    val forkReleased = CompletableFuture<Unit>()
    val forkResult = runBlocking {
      val callerScope = this
      virtualThreadTasks {
        val fork = fork("shared") {
          forkReleased.await()
          "done"
        }
        val awaiter = callerScope.launch {
          fork.awaitShared()
        }
        delay(50.milliseconds)
        awaiter.cancelAndJoin()
        assertThat(fork.isDone).isFalse()
        forkReleased.complete(Unit)
        fork
      }
    }

    assertThat(forkResult.join()).isEqualTo("done")
  }

  /** The plain `await` is for a one-shot future. This test pins the hazard that [awaitShared] avoids. */
  @Test
  fun `a cancelled awaiter of the plain await cancels the fork and ends the group`() {
    val forkCancelled = CompletableFuture<Unit>()
    assertThatThrownBy {
      runBlocking {
        val callerScope = this
        virtualThreadTasks {
          val fork = fork("one-shot") {
            try {
              awaitCancellation()
            }
            catch (e: CancellationException) {
              forkCancelled.complete(Unit)
              throw e
            }
          }
          val awaiter = callerScope.launch {
            fork.await()
          }
          delay(50.milliseconds)
          awaiter.cancelAndJoin()
        }
      }
    }.isInstanceOf(CancellationException::class.java)

    assertThat(forkCancelled.orTimeout(5, java.util.concurrent.TimeUnit.SECONDS).join()).isEqualTo(Unit)
  }

  @Test
  fun `a fork sees the telemetry context of the caller`() {
    val key = ContextKey.named<String>("VirtualThreadTasksTest")
    val seen = Context.current().with(key, "from the caller").makeCurrent().use {
      runBlocking {
        virtualThreadTasks {
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
      virtualThreadTasks {
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
