// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.path

import com.intellij.platform.eel.SafeDeferred
import com.intellij.platform.eel.toSafeDeferred
import com.intellij.platform.util.coroutines.childScope
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beInstanceOf
import io.kotest.matchers.types.shouldBeSameInstanceAs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import org.junit.jupiter.api.Test
import java.io.IOException
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
  fun `cancellation with a cause in await`(): Unit = runBlocking {
    val deferred = async {
      awaitCancellation()
    }
    deferred.cancel(CancellationException("hello").apply { initCause(IOException("boom")) })

    val err = shouldThrow<SafeDeferred.FailedDeferred> { SafeDeferred(deferred).await() }
    err.cause should beInstanceOf<IOException>()
    err.cause.message shouldBe "boom"
  }

  @Test
  fun `failure of a sibling in await`(): Unit = runBlocking {
    val job = Job()
    try {
      // The root job does not handle the failure, so without a handler it would reach the default one.
      val scope = CoroutineScope(job + CoroutineExceptionHandler { _, _ -> })
      val deferred = scope.async { awaitCancellation() }
      scope.launch { throw IOException("boom") }

      val err = shouldThrow<SafeDeferred.FailedDeferred> { SafeDeferred(deferred).await() }
      err.cause should beInstanceOf<IOException>()
      err.cause.message shouldBe "boom"
    }
    finally {
      job.cancel()
    }
  }

  @Test
  fun `mapper replaces an error in await`(): Unit = runBlocking {
    supervisorScope {
      val received = mutableListOf<Throwable>()
      val safeDeferred = async { error("oops") }.toSafeDeferred { err ->
        received += err
        IOException("mapped")
      }

      val err = shouldThrow<SafeDeferred.FailedDeferred> { safeDeferred.await() }
      err.cause should beInstanceOf<IOException>()
      err.cause.message shouldBe "mapped"
      received.single().message shouldBe "oops"
    }
  }

  @Test
  fun `mapper gets the root cause of a cancellation in await`(): Unit = runBlocking {
    val received = mutableListOf<Throwable>()
    val deferred = async { awaitCancellation() }
    deferred.cancel(CancellationException("hello").apply { initCause(IOException("boom")) })
    val safeDeferred = deferred.toSafeDeferred { err ->
      received += err
      IllegalStateException("mapped")
    }

    val err = shouldThrow<SafeDeferred.FailedDeferred> { safeDeferred.await() }
    err.cause.message shouldBe "mapped"
    received.single() should beInstanceOf<IOException>()
  }

  @Test
  fun `mapper is not called for a cancellation without a cause`(): Unit = runBlocking {
    val received = mutableListOf<Throwable>()
    val deferred = async { awaitCancellation() }
    deferred.cancel(CancellationException("hello"))
    val safeDeferred = deferred.toSafeDeferred {
      error("Not supposed to be called")
    }

    shouldThrow<SafeDeferred.CancelledDeferred> { safeDeferred.await() }
  }

  @Test
  fun `mapper that returns null keeps a cancellation in await`(): Unit = runBlocking {
    val deferred = async { awaitCancellation() }
    deferred.cancel(CancellationException("hello"))

    val err = shouldThrow<SafeDeferred.CancelledDeferred> { deferred.toSafeDeferred { null }.await() }
    err.cause.message shouldBe "hello"
  }

  @Test
  fun `nested failure is not wrapped twice in await`(): Unit = runBlocking {
    supervisorScope {
      val inner = SafeDeferred(async { error("oops") })
      val outer = SafeDeferred(async { inner.await() })

      val err = shouldThrow<SafeDeferred.FailedDeferred> { outer.await() }
      err.cause should beInstanceOf<IllegalStateException>()
      err.cause.message shouldBe "oops"
    }
  }

  @Test
  fun `nested failure is mapped in await`(): Unit = runBlocking {
    supervisorScope {
      val inner = SafeDeferred(async { error("oops") })
      val outer = async { inner.await() }.toSafeDeferred { IOException("mapped") }

      val err = shouldThrow<SafeDeferred.FailedDeferred> { outer.await() }
      err.cause should beInstanceOf<IOException>()
      err.cause.message shouldBe "mapped"
    }
  }

  @Test
  fun `nested cancellation stays a cancellation in await`(): Unit = runBlocking {
    supervisorScope {
      val innerDeferred = async { awaitCancellation() }
      innerDeferred.cancel(CancellationException("hello"))
      val inner = SafeDeferred(innerDeferred)
      val outer = SafeDeferred(async { inner.await() })

      val err = shouldThrow<SafeDeferred.CancelledDeferred> { outer.await() }
      err.cause.message shouldBe "hello"
    }
  }

  @Test
  fun `failure of a nested sibling is not wrapped twice in await`(): Unit = runBlocking {
    val job = Job()
    try {
      // The root job does not handle the failure, so without a handler it would reach the default one.
      val scope = CoroutineScope(job + CoroutineExceptionHandler { _, _ -> })
      val inner = SafeDeferred(CompletableDeferred<Unit>().apply { completeExceptionally(IOException("boom")) })
      val received = mutableListOf<Throwable>()
      val deferred = scope.async { awaitCancellation() }.toSafeDeferred { err ->
        received += err
        null
      }
      // The sibling fails with FailedDeferred, so the deferred gets a CancellationException caused by FailedDeferred.
      scope.launch { inner.await() }

      val err = shouldThrow<SafeDeferred.FailedDeferred> { deferred.await() }
      err.cause should beInstanceOf<IOException>()
      err.cause.message shouldBe "boom"
      received.single() should beInstanceOf<IOException>()
    }
    finally {
      job.cancel()
    }
  }

  @Test
  fun `cancellation caused by a failed deferred is not wrapped twice`(): Unit = runBlocking {
    val deferred = async { awaitCancellation() }
    deferred.cancel(CancellationException("hello").apply { initCause(SafeDeferred.FailedDeferred(IOException("boom"))) })
    deferred.join()

    val state = SafeDeferred(deferred).state
    state should beInstanceOf<SafeDeferred.State.Failed>()
    (state as SafeDeferred.State.Failed).error should beInstanceOf<IOException>()

    val err = shouldThrow<SafeDeferred.FailedDeferred> { SafeDeferred(deferred).await() }
    err.cause should beInstanceOf<IOException>()
    err.cause.message shouldBe "boom"
  }

  @Test
  fun `cancellation caused by a cancelled deferred stays a cancellation`(): Unit = runBlocking {
    val deferred = async { awaitCancellation() }
    val innerCancellation = CancellationException("inner")
    deferred.cancel(CancellationException("hello").apply { initCause(SafeDeferred.CancelledDeferred(innerCancellation)) })
    deferred.join()
    val safeDeferred = deferred.toSafeDeferred {
      error("Not supposed to be called")
    }

    val mapped = (safeDeferred.state as SafeDeferred.State.UnmappedError).await()
    mapped should beInstanceOf<SafeDeferred.State.Canceled>()
    mapped.error shouldBeSameInstanceAs innerCancellation

    val err = shouldThrow<SafeDeferred.CancelledDeferred> { safeDeferred.await() }
    err.cause shouldBeSameInstanceAs innerCancellation
  }

  @Test
  fun `state of cancelled with a cause agrees with await`(): Unit = runBlocking {
    val deferred = async { awaitCancellation() }
    deferred.cancel(CancellationException("hello").apply { initCause(IOException("boom")) })
    runCatching { deferred.await() }

    val safeDeferred = SafeDeferred(deferred)
    val state = safeDeferred.state
    state should beInstanceOf<SafeDeferred.State.Failed>()
    (state as SafeDeferred.State.Failed).error should beInstanceOf<IOException>()
    shouldThrow<SafeDeferred.FailedDeferred> { state.throwWrapped() }.cause should beInstanceOf<IOException>()
  }

  @Test
  fun `state of failed without a mapper is cached`(): Unit = runBlocking {
    supervisorScope {
      val deferred = async { error("oops") }
      deferred.join()

      val safeDeferred = SafeDeferred(deferred)
      val state = safeDeferred.state
      state should beInstanceOf<SafeDeferred.State.Failed>()
      (state as SafeDeferred.State.Failed).error.message shouldBe "oops"
      safeDeferred.state shouldBeSameInstanceAs state
    }
  }

  @Test
  fun `state of failed with a mapper is unmapped until mapped`(): Unit = runBlocking {
    supervisorScope {
      val received = mutableListOf<Throwable>()
      val deferred = async { error("oops") }
      deferred.join()
      val safeDeferred = deferred.toSafeDeferred { err ->
        received += err
        IOException("mapped")
      }

      val state = safeDeferred.state
      state should beInstanceOf<SafeDeferred.State.UnmappedError>()
      received shouldBe emptyList()

      val mapped = (state as SafeDeferred.State.UnmappedError).await()
      mapped should beInstanceOf<SafeDeferred.State.Failed>()
      mapped.error.message shouldBe "mapped"
      shouldThrow<SafeDeferred.FailedDeferred> { mapped.throwWrapped() }.cause.message shouldBe "mapped"
      received.single().message shouldBe "oops"

      // The mapped state is cached, so the mapper does not run again.
      safeDeferred.state shouldBeSameInstanceAs mapped
      (safeDeferred.state as SafeDeferred.State.Failed).error.message shouldBe "mapped"
      received.size shouldBe 1
    }
  }

  @Test
  fun `state after await is mapped`(): Unit = runBlocking {
    supervisorScope {
      val received = mutableListOf<Throwable>()
      val safeDeferred = async { error("oops") }.toSafeDeferred { err ->
        received += err
        IOException("mapped")
      }

      shouldThrow<SafeDeferred.FailedDeferred> { safeDeferred.await() }.cause.message shouldBe "mapped"

      val state = safeDeferred.state
      state should beInstanceOf<SafeDeferred.State.Failed>()
      (state as SafeDeferred.State.Failed).error.message shouldBe "mapped"
      received.size shouldBe 1
    }
  }

  @Test
  fun `unmapped state of a cancellation without a cause becomes a cancellation`(): Unit = runBlocking {
    val deferred = async { awaitCancellation() }
    deferred.cancel(CancellationException("hello"))
    deferred.join()
    val safeDeferred = deferred.toSafeDeferred {
      error("Not supposed to be called")
    }

    val state = safeDeferred.state
    state should beInstanceOf<SafeDeferred.State.UnmappedError>()
    val mapped = (state as SafeDeferred.State.UnmappedError).await()
    mapped should beInstanceOf<SafeDeferred.State.Canceled>()
    mapped.error.message shouldBe "hello"
    shouldThrow<SafeDeferred.CancelledDeferred> { mapped.throwWrapped() }
  }

  @Test
  fun `unmapped state of a cancellation with a cause maps the root cause`(): Unit = runBlocking {
    val received = mutableListOf<Throwable>()
    val deferred = async { awaitCancellation() }
    deferred.cancel(CancellationException("hello").apply { initCause(IOException("boom")) })
    deferred.join()
    val safeDeferred = deferred.toSafeDeferred { err ->
      received += err
      IllegalStateException("mapped")
    }

    val mapped = (safeDeferred.state as SafeDeferred.State.UnmappedError).await()
    mapped should beInstanceOf<SafeDeferred.State.Failed>()
    mapped.error.message shouldBe "mapped"
    received.single().message shouldBe "boom"
  }

  @Test
  fun `unmapped state with a mapper that returns null keeps the raw error`(): Unit = runBlocking {
    supervisorScope {
      val deferred = async { error("oops") }
      deferred.join()
      val safeDeferred = deferred.toSafeDeferred { null }

      val mapped = (safeDeferred.state as SafeDeferred.State.UnmappedError).await()
      mapped should beInstanceOf<SafeDeferred.State.Failed>()
      mapped.error should beInstanceOf<IllegalStateException>()
      mapped.error.message shouldBe "oops"
    }
  }

  @Test
  fun `map keeps the mapper`(): Unit = runBlocking {
    supervisorScope {
      val original = async<Int> { error("oops") }.toSafeDeferred { IOException("mapped") }
      val derived = original.map { it + 1 }

      val err = shouldThrow<SafeDeferred.FailedDeferred> { derived.await() }
      err.cause should beInstanceOf<IOException>()
      err.cause.message shouldBe "mapped"
    }
  }

  @Test
  fun `map passes an error of the block`(): Unit = runBlocking {
    val original = SafeDeferred(GlobalScope.async(start = CoroutineStart.UNDISPATCHED) { 1 })
    val derived = original.map<Int> { error("block") }

    val err = shouldThrow<SafeDeferred.FailedDeferred> { derived.await() }
    err.cause.message shouldBe "block"
    (original.state as SafeDeferred.State.Completed).value shouldBe 1
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
        is SafeDeferred.State.Unsuccessful -> error(it)
      }
    }
  }

  @Test
  fun `invokeWhenCompleted of canceled`() = runBlocking {
    SafeDeferred(async { awaitCancellation() }.apply { cancel() }).invokeWhenCompleted {
      when (it) {
        is SafeDeferred.State.Canceled -> Unit
        is SafeDeferred.State.Completed, is SafeDeferred.State.Failed, is SafeDeferred.State.UnmappedError -> error(it)
      }
    }
  }

  @Test
  fun `invokeWhenCompleted of error`() = runBlocking {
    supervisorScope {
      SafeDeferred(async { error("oops") }).invokeWhenCompleted {
        when (it) {
          is SafeDeferred.State.Failed -> it.error.message shouldBe "oops"
          is SafeDeferred.State.Canceled, is SafeDeferred.State.Completed, is SafeDeferred.State.UnmappedError -> error(it)
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

