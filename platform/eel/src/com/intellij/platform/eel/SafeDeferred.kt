// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.completeWith
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.future.asCompletableFuture
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.CompletableFuture
import kotlin.coroutines.cancellation.CancellationException

/**
 * A wrapper around a [Deferred]. Intended to be used at API levels.
 *
 * Reasons of existence:
 * * **Encapsulation.**
 *   Original [Deferred] exposes jobs, coroutine scopes, etc.
 *   An API user could accidentally interact with these internals with undefined behavior.
 * * **Fixing the silent killer flaw.**
 *   The problem is documented in [Deferred.await].
 *   A Deferred can be created inside one coroutine context and awaited in another context.
 *   If the creation coroutine scope is canceled,
 *   [Deferred.await] throws [kotlinx.coroutines.CancellationException] and silently destroys an unrelated coroutine context.
 *   Instead of forcing everyone call `ensureActive` in a catch-block, this wrapper does that itself.
 * * **Pointing at the code that throws errors.**
 *   [Deferred.await] throws an exception that destroyed the job of the deferred.
 *   It does not show the place where [Deferred.await] is actually called.
 */
@ApiStatus.Experimental
class SafeDeferred<T>(private val deferred: Deferred<T>) {
  sealed class DeferredException(override val cause: Throwable) : RuntimeException(cause.message, cause)

  /**
   * Unlike [CancellationException], it is not a control-flow exception.
   */
  class CancelledDeferred(override val cause: CancellationException) : DeferredException(cause)

  /**
   * Wraps errors from [Deferred.await], providing a stack trace with [SafeDeferred.await].
   */
  class FailedDeferred(cause: Throwable) : DeferredException(cause)

  /**
   * Does the same as [Deferred.await] but throws [CancelledDeferred] instead of [CancellationException]
   * and [FailedDeferred] when the deferred fails.
   */
  @ThrowsChecked(DeferredException::class)
  suspend fun await(): T =
    try {
      deferred.await()
    }
    catch (err: CancellationException) {
      currentCoroutineContext().ensureActive()
      throw CancelledDeferred(err)
    }
    catch (err: Exception) {
      throw FailedDeferred(err)
    }

  sealed interface State<T> {
    object Active : State<Any?>

    sealed interface Finished<T> : State<T>
    class Completed<T>(val value: T) : Finished<T>

    sealed interface FinishedUnsuccessfully : Finished<Any?> {
      val error: Throwable

      fun throwWrapped(): Nothing
    }

    class Canceled(override val error: CancellationException) : FinishedUnsuccessfully {
      override fun throwWrapped(): Nothing {
        throw CancelledDeferred(error)
      }
    }

    class Failed(override val error: Throwable) : FinishedUnsuccessfully {
      override fun throwWrapped(): Nothing {
        throw FailedDeferred(error)
      }
    }
  }

  /**
   * Replaces [Deferred.isActive], [Deferred.isCompleted], [Deferred.isCancelled], [Deferred.getCompleted], [Deferred.getCompletionExceptionOrNull].
   *
   * Works better for code like `if (isFailed) getCompletedExceptionOrNull()!!`.
   */
  @Suppress("UNCHECKED_CAST")
  @OptIn(ExperimentalCoroutinesApi::class)
  val state: State<T>
    get() {
      if (!deferred.isCompleted) {
        return State.Active as State<T>
      }

      val err = deferred.getCompletionExceptionOrNull()

      return when (err) {
        null -> State.Completed(deferred.getCompleted())
        is CancellationException -> State.Canceled(err) as State<T>
        else -> State.Failed(err) as State<T>
      }
    }

  /**
   * The same as [kotlinx.coroutines.future.asCompletableFuture]. Does NOT wrap exceptions.
   */
  fun asCompletableFuture(): CompletableFuture<T> {
    return deferred.asCompletableFuture()
  }

  /**
   * Does the same as [Deferred.invokeOnCompletion] with a bit different interface.
   *
   * This function has a different name by intention: it causes compilation errors during replacing [Deferred] with [SafeDeferred].
   * Otherwise, old calls could invoke something like `invokeOnCompletion { if (it == null) ... }`.
   * Here, `it == null` is a valid code for Deferred, but invalid for SafeDeferred, though it would compile.
   */
  fun invokeWhenCompleted(block: (State.Finished<T>) -> Unit) {
    deferred.invokeOnCompletion {
      @Suppress("UNCHECKED_CAST")
      block(state as State.Finished<T>)
    }
  }
}

@ApiStatus.Experimental
inline fun <A, B> SafeDeferred<A>.map(crossinline block: (A) -> B): SafeDeferred<B> {
  val result = CompletableDeferred<B>()
  invokeWhenCompleted {
    when (it) {
      is SafeDeferred.State.Completed -> result.completeWith(runCatching { block(it.value) })
      is SafeDeferred.State.Canceled -> result.cancel(it.error)
      is SafeDeferred.State.Failed -> result.completeExceptionally(it.error)
    }
  }
  return SafeDeferred(result)
}

@ApiStatus.Experimental
fun <K, V> MutableMap<K, SafeDeferred<V>>.computeDeferred(
  coroutineScope: CoroutineScope,
  key: K,
  factory: suspend (key: K) -> V,
): SafeDeferred<V> =
  compute(key) { key, old ->
    when (old?.state) {
      SafeDeferred.State.Active, is SafeDeferred.State.Completed -> old

      null, is SafeDeferred.State.FinishedUnsuccessfully -> SafeDeferred(coroutineScope.async {
        factory(key)
      })
    }
  }!!