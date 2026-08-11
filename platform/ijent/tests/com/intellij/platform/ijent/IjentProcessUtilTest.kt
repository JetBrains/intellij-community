// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ClassName")

package com.intellij.platform.ijent

import com.intellij.platform.eel.SafeDeferred
import com.intellij.platform.ijent.IjentUnavailableException.CommunicationFailure
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.io.IOException
import java.util.Collections
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Tests for the "resolvable canonical exit reason" invariant (see IJPL-245668).
 *
 * The goal is not that every coroutine *throws* [IjentUnavailableException] (cancellation is first-cause-wins and cannot
 * be rewritten), but that any coroutine which must surface the failure of a dead session can *resolve* the single
 * canonical [IjentUnavailableException] and rethrow it.
 */
class IjentProcessUtilTest {
  @Test
  fun `resolveDeadSessionReason returns the unwrapped IjentUnavailableException immediately`(): Unit = runBlocking {
    val canonical = CommunicationFailure("direct", null)
    val wrapped = CancellationException("cancelled", canonical)

    IjentUnavailableException.resolveDeadSessionReason(wrapped, 100.milliseconds) shouldBe canonical
  }

  @Test
  fun `resolveDeadSessionReason maps a low-level failure to the canonical reason`(): Unit {
    val ijentContext = IjentScope.IjentContext()
    val canonical = CommunicationFailure("canonical", null)

    runBlocking(ijentContext) {
      // The mediator publishes the authoritative reason.
      ijentContext.completeExitReason(canonical)

      IjentUnavailableException.resolveDeadSessionReason(IOException("low level"), 1.seconds) shouldBe canonical
    }
  }

  @Test
  fun `a non-unavailable failure does not mask the canonical reason`(): Unit {
    val ijentContext = IjentScope.IjentContext()
    val canonical = CommunicationFailure("canonical", null)

    runBlocking(ijentContext) {
      ijentContext.completeExitReason(canonical)

      // Even though a plain "oops" would win a cancellation race, the canonical reason must prevail.
      IjentUnavailableException.resolveDeadSessionReason(IllegalStateException("oops"), 1.seconds) shouldBe canonical
    }
  }

  @Test
  fun `resolveDeadSessionReason falls back to the original error when no reason arrives within the bound`(): Unit {
    val ijentContext = IjentScope.IjentContext()
    val lowLevel = IOException("low level")

    runBlocking(ijentContext) {
      // exitReason is never completed, so the bounded await times out and the original error is returned.
      IjentUnavailableException.resolveDeadSessionReason(lowLevel, 100.milliseconds) shouldBe lowLevel
    }
  }

  @Test
  fun `SafeDeferred await surfaces the canonical reason instead of FailedDeferred`(): Unit {
    val ijentContext = IjentScope.IjentContext()
    val canonical = CommunicationFailure("canonical", null)

    runBlocking(ijentContext) {
      ijentContext.completeExitReason(canonical)

      val backing = CompletableDeferred<Int>()
      // Reproduces IJPL-245668: the backing deferred fails with a raw low-level exception.
      backing.completeExceptionally(IOException("Process exited normally"))

      val safeDeferred = SafeDeferred(backing, IJENT_DEAD_SESSION_SAFE_DEFERRED_MAPPER)

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
  fun `sibling coroutines resolve the canonical reason under production-like topology`(): Unit = runBlocking {
    val ijentContext = IjentScope.IjentContext()
    val canonical = CommunicationFailure("session died", null)

    // A supervisor parent that swallows uncaught exceptions, and a non-supervisor child carrying the IjentContext -
    // this mirrors IjentSessionMediatorUtils.createProcessScope.
    val supervisor = CoroutineScope(Dispatchers.Default + SupervisorJob() + CoroutineExceptionHandler { _, _ -> })
    try {
      val child = CoroutineScope(supervisor.coroutineContext + Job(supervisor.coroutineContext.job) + ijentContext)

      val resolvedReasons = Collections.synchronizedList(mutableListOf<Throwable>())

      val jobs = (0 until 3).map { index ->
        child.launch {
          // A low-level failure arrives; the boundary resolves the canonical reason (bounded await bridges the race).
          resolvedReasons.add(IjentUnavailableException.resolveDeadSessionReason(IOException("low level $index"), 3.seconds))
        }
      }

      // The mediator publishes the authoritative reason after the siblings are already waiting.
      ijentContext.completeExitReason(canonical)

      jobs.joinAll()

      resolvedReasons.size shouldBe jobs.size
      resolvedReasons.forEach { it shouldBe canonical }
    }
    finally {
      supervisor.cancel()
    }
  }
}
