// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.community.ui.actions.unavailable

import com.intellij.testFramework.common.timeoutRunBlocking
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

@Timeout(30)
internal class OnceTaskTest {
  @Test
  fun `decision survives cancellation while child cleanup is pending`(): Unit = timeoutRunBlocking {
    val task = TestOnceTask()
    val decision = 42
    val cleanupStarted = CompletableDeferred<Unit>()
    val finishCleanup = CompletableDeferred<Unit>()
    val worker = launch {
      task.getOrCompute({}) {
        coroutineScope {
          val child = launch(start = CoroutineStart.UNDISPATCHED) {
            try {
              awaitCancellation()
            }
            finally {
              withContext(NonCancellable) {
                cleanupStarted.complete(Unit)
                finishCleanup.await()
              }
            }
          }
          child.cancel()
          decision
        }
      }
    }
    cleanupStarted.await()
    worker.cancel()
    finishCleanup.complete(Unit)
    worker.join()

    repeat(2) {
      assertEquals(decision, task.getOrCompute({}) { error("The task must not run again") })
    }
  }

  @Test
  fun `cancellation before a decision permits another attempt`(): Unit = timeoutRunBlocking {
    val task = TestOnceTask()
    val started = CompletableDeferred<Unit>()
    val worker = launch {
      task.getOrCompute({}) {
        started.complete(Unit)
        awaitCancellation()
      }
    }
    started.await()
    worker.cancelAndJoin()
    assertEquals(42, task.getOrCompute({}) { 42 })
  }

  @Test
  fun `ordinary failure permits another attempt`(): Unit = timeoutRunBlocking {
    val task = TestOnceTask()
    val failure = IllegalStateException("The dialog could not open")
    try {
      task.getOrCompute({}) { throw failure }
      error("The failure must be thrown")
    }
    catch (e: IllegalStateException) {
      assertEquals(failure.message, e.message)
    }
    assertEquals(42, task.getOrCompute({}) { 42 })
  }

  private class TestOnceTask : OnceTask<Int, Unit>() {
    override suspend fun <R> executeUnderLockIfNotAlreadyAcquired(f: suspend () -> R): R = f()
  }
}
