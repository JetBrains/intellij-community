// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.spi

import com.intellij.platform.ijent.ParentOfIjentScopes
import com.intellij.platform.util.coroutines.childScope
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.io.IOException
import java.util.Collections
import kotlin.coroutines.cancellation.CancellationException

/**
 * Tests for the decision made by [IjentSessionMediatorUtils.createProcessScope] about which failures of an IJent session
 * are worth bothering the owner of the parent scope with (see IJPL-252032).
 */
class IjentSessionMediatorUtilsTest {
  @Test
  fun `a low-level failure that loses the shutdown race is not propagated to the parent scope`(): Unit = runBlocking {
    withParentScope { parent, uncaught ->
      val ijentScope = IjentSessionMediatorUtils.createProcessScope(ParentOfIjentScopes(parent), "test-session")

      val inFlight = CompletableDeferred<Unit>()
      // Imitates a call that is in flight when the transport gets shut down: cancelling the session scope closes the
      // transport, and the call then fails with a raw low-level exception of its own instead of a CancellationException.
      // gRPC does exactly this with `StatusException: UNAVAILABLE: Channel shutdown invoked`.
      ijentScope.s.launch {
        try {
          inFlight.complete(Unit)
          awaitCancellation()
        }
        catch (_: CancellationException) {
          throw IOException("UNAVAILABLE: Channel shutdown invoked")
        }
      }
      inFlight.await()

      // The application drops the session, just like `bodyLimitedCoroutineScope` does at the end of a successful test.
      parent.coroutineContext.cancelChildren(CancellationException("A successful end of the test body"))
      ijentScope.s.coroutineContext.job.join()

      uncaught.shouldBeEmpty()
    }
  }

  @Test
  fun `a low-level failure in a live session is still propagated to the parent scope`(): Unit = runBlocking {
    withParentScope { parent, uncaught ->
      val ijentScope = IjentSessionMediatorUtils.createProcessScope(ParentOfIjentScopes(parent), "test-session")

      // Nobody asked to close this session, so the failure is a real one and must reach the application.
      ijentScope.s.launch {
        throw IOException("something broke inside a healthy session")
      }
      ijentScope.s.coroutineContext.job.join()

      uncaught.map { it.message }.shouldContainExactly("something broke inside a healthy session")
    }
  }

  /**
   * Runs [body] with a scope that imitates the application: a supervisor, so that a failure rethrown into it by
   * [IjentSessionMediatorUtils.createProcessScope] is reported to the exception handler instead of tearing the scope down.
   * Everything the handler receives is what a real application would see as an error.
   */
  private suspend fun withParentScope(body: suspend (CoroutineScope, List<Throwable>) -> Unit): Unit = coroutineScope {
    val uncaught = Collections.synchronizedList(mutableListOf<Throwable>())
    val parent = childScope("application", CoroutineExceptionHandler { _, err -> uncaught.add(err) }, supervisor = true)
    try {
      body(parent, uncaught)
    }
    finally {
      parent.cancel()
    }
  }
}
