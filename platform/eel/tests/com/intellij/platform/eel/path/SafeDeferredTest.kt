// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.path

import com.intellij.platform.eel.SafeDeferred
import com.intellij.platform.util.coroutines.childScope
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beInstanceOf
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import org.junit.jupiter.api.Test
import java.util.concurrent.CancellationException
import kotlin.time.Duration.Companion.milliseconds

@Suppress("checkedExceptions")
@OptIn(DelicateCoroutinesApi::class)
class SafeDeferredTest {
  @Test
  fun `cancellation in await`(): Unit = runBlocking {
    val deferred = async {
      awaitCancellation()
    }
    deferred.cancel(CancellationException("hello"))

    val safeDeferred = SafeDeferred(deferred)
    val err = shouldThrow<SafeDeferred.CancelledDeferred> { safeDeferred.await() }
    err.cause.message shouldBe "hello"
  }

  @Test
  fun `error in await`(): Unit = runBlocking {
    supervisorScope {
      val deferred = async {
        error("oops")
      }

      val safeDeferred = SafeDeferred(deferred)
      val err = shouldThrow<SafeDeferred.FailedDeferred> { safeDeferred.await() }
      err.cause should beInstanceOf<IllegalStateException>()
      err.cause.message shouldBe "oops"
    }
  }

  @Test
  fun `state of active`(): Unit = runBlocking {
    val deferred = async {
      awaitCancellation()
    }

    try {
      val safeDeferred = SafeDeferred(deferred)
      safeDeferred.state shouldBe SafeDeferred.State.Active
    }
    finally {
      deferred.cancel()
    }
  }

  @Test
  fun `state of completed`() {
    val safeDeferred = SafeDeferred(GlobalScope.async(start = CoroutineStart.UNDISPATCHED) { 12345 })
    safeDeferred.state should beInstanceOf<SafeDeferred.State.Completed<*>>()
    (safeDeferred.state as SafeDeferred.State.Completed).value shouldBe 12345
  }

  @Test
  fun `state of cancelled`() {
    val deferred = GlobalScope.async {
      awaitCancellation()
    }
    deferred.cancel(CancellationException("hello"))

    runCatching {
      runBlocking {
        deferred.await()
      }
    }

    val safeDeferred = SafeDeferred(deferred)
    safeDeferred.state should beInstanceOf<SafeDeferred.State.Canceled>()
    (safeDeferred.state as SafeDeferred.State.Canceled).error.message shouldBe "hello"
  }

  @Test
  fun `state of error`() {
    val deferred = GlobalScope.async(start = CoroutineStart.UNDISPATCHED) {
      error("oops")
    }

    val safeDeferred = SafeDeferred(deferred)
    safeDeferred.state should beInstanceOf<SafeDeferred.State.Failed>()
    val err = (safeDeferred.state as SafeDeferred.State.Failed).error
    err should beInstanceOf<IllegalStateException>()
    err.message shouldBe "oops"
  }

  @Test
  fun `invokeWhenCompleted of completed`() = runBlocking {
    SafeDeferred(async { 12345 }).invokeWhenCompleted {
      when (it) {
        is SafeDeferred.State.Completed -> it.value shouldBe 12345
        is SafeDeferred.State.FinishedUnsuccessfully -> error(it)
      }
    }
  }

  @Test
  fun `invokeWhenCompleted of canceled`() = runBlocking {
    SafeDeferred(async { awaitCancellation() }.apply { cancel() }).invokeWhenCompleted {
      when (it) {
        is SafeDeferred.State.Canceled -> Unit
        is SafeDeferred.State.Completed, is SafeDeferred.State.Failed -> error(it)
      }
    }
  }

  @Test
  fun `invokeWhenCompleted of error`() = runBlocking {
    supervisorScope {
      SafeDeferred(async { error("oops") }).invokeWhenCompleted {
        when (it) {
          is SafeDeferred.State.Failed -> it.error.message shouldBe "oops"
          is SafeDeferred.State.Canceled, is SafeDeferred.State.Completed -> error(it)
        }
      }
    }
  }

  @Test
  fun `mass cancellation`() {
    shouldThrow<CancellationException> {
      runBlocking {
        val safeDeferred = SafeDeferred(async(start = CoroutineStart.UNDISPATCHED) {
          awaitCancellation()
        })
        childScope("sdfsdfgsd", supervisor = false).launch(start = CoroutineStart.UNDISPATCHED) {
          safeDeferred.await()
        }
        delay(100.milliseconds)
        cancel()
      }
    }
  }
}
