// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ClassName")

package com.intellij.platform.ijent

import com.intellij.platform.eel.SafeDeferred
import com.intellij.platform.ijent.IjentUnavailableException.ClosedByApplication
import com.intellij.platform.ijent.IjentUnavailableException.CommunicationFailure
import com.intellij.platform.ijent.spi.IjentSessionMediatorUtils
import com.intellij.testFramework.common.timeoutRunBlocking
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Test
import java.io.IOException
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Tests for the "resolvable canonical exit reason" invariant (see IJPL-245668).
 *
 * The goal is not that every coroutine *throws* [IjentUnavailableException] (cancellation is first-cause-wins and cannot
 * be rewritten), but that any coroutine which must surface the failure of a dead session can *resolve* the single
 * canonical [IjentUnavailableException] and rethrow it.
 */
@OptIn(DelicateCoroutinesApi::class)
class IjentProcessUtilTest {
  @Test
  fun `expected process exit stores ClosedByApplication without attaching it to cancellation`(): Unit = runBlocking {
    val ijentScope = ParentOfIjentScopes(this).createIjentScope("test")

    withContext(ijentScope) {
      val thrown = shouldThrow<ClosedByApplication> {
        IjentSessionMediatorUtils.ijentProcessExitCodeHandler(
          ijentLabel = "test",
          lastStderrMessages = MutableSharedFlow<String?>(),
          exitCode = -1,
          isExitExpected = true,
        )
      }

      thrown.cause shouldBe null
      ijentScope.resolveExitReason(1.seconds).shouldBeInstanceOf<IjentUnavailableException.ClosedByApplication>()
    }
  }

  @Test
  fun `SafeDeferred await surfaces the canonical reason instead of FailedDeferred`(): Unit = runBlocking {
    val dummyHandler = CoroutineExceptionHandler { _, _ -> }
    val ijentScope = ParentOfIjentScopes(CoroutineScope(SupervisorJob() + dummyHandler)).createIjentScope("test")
    val canonical = CommunicationFailure("canonical", null)

    withContext(ijentScope) {
      ijentScope.destroy(canonical, isRootCause = true)

      val backing = CompletableDeferred<Int>()
      // Reproduces IJPL-245668: the backing deferred fails with a raw low-level exception.
      backing.completeExceptionally(IOException("Process exited normally"))

      val safeDeferred = SafeDeferred(backing) { ijentScope.resolveExitReason() }

      val thrown = shouldThrow<SafeDeferred.FailedDeferred> { safeDeferred.await() }
        .cause
        .shouldBeInstanceOf<IjentUnavailableException>()
      thrown shouldBe canonical
    }
  }

  @Test
  fun `SafeDeferred without a mapper keeps the default FailedDeferred behavior`(): Unit = runBlocking {
    val backing = CompletableDeferred<Int>()
    backing.completeExceptionally(IOException("boom"))

    val safeDeferred = SafeDeferred(backing)

    shouldThrow<SafeDeferred.FailedDeferred> { safeDeferred.await() }
  }

  @Test
  fun `any failure in any coroutine of ijent terminates the whole session`(): Unit = timeoutRunBlocking {
    val dummyHandler = CoroutineExceptionHandler { _, _ -> }
    val ijentScope = ParentOfIjentScopes(CoroutineScope(SupervisorJob() + dummyHandler)).createIjentScope("IjentProcessUtilTest")
    ijentScope.s.launch {
      delay(100.milliseconds)
      error("oops")
    }

    val err = shouldThrowAny {
      ijentScope.wrapErrors {
        ijentScope.s.async { delay(1.seconds) }.await()
      }
    }
    err.message shouldContain "oops"
  }

  @Test
  fun `IjentScope rethrows the root cause`(): Unit = timeoutRunBlocking {
    val rightErrorMessage = "This is the right error (${Random.nextInt()})"
    val dummyHandler = CoroutineExceptionHandler { _, _ -> }
    val ijentScope = ParentOfIjentScopes(CoroutineScope(SupervisorJob() + dummyHandler)).createIjentScope("IjentProcessUtilTest")

    val deferred = ijentScope.s.async {
      delay(10.seconds)
    }

    ijentScope.s.launch {
      delay(10.milliseconds)
      throw CommunicationFailure("This error should not propagate", null)
    }

    ijentScope.s.launch {
      delay(10.milliseconds)
      throw IllegalStateException("This error should not propagate either", null)
    }

    ijentScope.s.launch(start = CoroutineStart.UNDISPATCHED) {
      try {
        delay(200.milliseconds)
      }
      catch (ex: Throwable) {
        throw ex
      }
      finally {
        val err = ClosedByApplication(rightErrorMessage, null)
        ijentScope.destroy(err, true)
        throw CommunicationFailure("And even this error should not propagate", null)
      }
    }

    val caughtErr = shouldThrow<ClosedByApplication> {
      ijentScope.wrapErrors {
        deferred.await()
      }
    }
    caughtErr.message shouldBe rightErrorMessage
  }
}
