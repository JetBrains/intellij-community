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
import java.util.concurrent.atomic.AtomicReference
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
@Suppress("RethrowControlFlowExceptionWithUtil")
@ApiStatus.Experimental
class SafeDeferred<T> private constructor(
  private val deferred: Deferred<T>,

  private val mappedErrorState: AtomicReference<State.MappedError?>,

  /**
   * An optional, opt-in mapper for failures of a dead backing session.
   *
   * When the backing [deferred] fails or is canceled, [await] first offers the failure to this mapper.
   * For a [CancellationException], the mapper gets its first cause that is not a [CancellationException].
   *
   * Wraps only [Exception], rethrows other subclasses of [Throwable] as is.
   *
   * [deadSessionMapper] never gets a [CancellationException] itself. Such a [CancellationException] is rethrown wrapped into [CancelledDeferred].
   *
   * [deadSessionMapper] may be called many times and concurrently. Therefore, it is supposed that [deadSessionMapper] is idempotent.
   *
   * [deadSessionMapper] must not return a [CancellationException].
   * [FailedDeferred] rejects it, so [await] and [State.Failed.throwWrapped] then throw [IllegalArgumentException].
   *
   * If the mapper returns a non-null throwable, [await] wraps that throwable into [FailedDeferred]
   * in place of the raw failure. This lets owners (e.g. IJent) surface a canonical domain exception as the
   * [FailedDeferred] cause instead of a low-level one, while still honoring the [ThrowsChecked] contract of [await].
   * Non-owners keep the default behavior (the parameter defaults to `null`).
   */
  internal val deadSessionMapper: (suspend (Throwable) -> Throwable?)?,
) {
  constructor(deferred: Deferred<T>) : this(deferred, null)

  /**
   * See docs for [SafeDeferred.deadSessionMapper].
   */
  constructor(
    deferred: Deferred<T>,
    deadSessionMapper: (suspend (Throwable) -> Throwable?)?,
  ) : this(deferred, AtomicReference(null), deadSessionMapper)

  sealed class DeferredException(override val cause: Throwable) : RuntimeException(cause.message, cause)

  /**
   * Unlike [CancellationException], it is not a control-flow exception.
   */
  class CancelledDeferred(override val cause: CancellationException) : DeferredException(cause)

  /**
   * Wraps errors from [Deferred.await], providing a stack trace with [SafeDeferred.await].
   */
  class FailedDeferred(override val cause: Throwable) : DeferredException(cause) {
    init {
      if (cause is CancellationException) {
        throw IllegalArgumentException("FailedDeferred should not wrap a CancellationException itself", cause)
      }
    }
  }

  /**
   * Does the same as [Deferred.await] but throws [FailedDeferred] when the deferred fails.
   *
   * A [CancellationException] often wraps a valuable exception in Kotlin coroutines.
   * In that case, this function throws the wrapped exception inside [FailedDeferred].
   * It throws [CancelledDeferred] only for a [CancellationException] with no such cause and no mapped error.
   */
  @ThrowsChecked(DeferredException::class)
  suspend fun await(): T =
    try {
      deferred.await()
    }
    catch (err: Exception) {
      if (err is CancellationException) {
        currentCoroutineContext().ensureActive()
      }
      while (true) {
        mappedErrorState.get()?.throwWrapped()
        mappedErrorState.compareAndSet(null, convertException(err) { deadSessionMapper?.invoke(it) ?: it })
      }
      error("Unreachable")
    }

  sealed interface State<T> {
    object Active : State<Any?>

    sealed interface Finished<T> : State<T>
    class Completed<T>(val value: T) : Finished<T>

    sealed interface Unsuccessful : Finished<Any?>

    /**
     * The deferred failed, but [SafeDeferred.deadSessionMapper] did not map the error yet.
     *
     * The mapper is a suspend function, so [SafeDeferred.state] cannot call it.
     * Call [await] to get the [MappedError].
     */
    class UnmappedError(private val owner: SafeDeferred<*>, internal val unmappedError: Throwable) : Unsuccessful {
      suspend fun await(): MappedError {
        // Reminder: `deadSessionMapper` is supposed to be idempotent.
        while (true) {
          owner.mappedErrorState.get()?.let {
            return it
          }

          owner.mappedErrorState.compareAndSet(null, convertException(unmappedError) { owner.deadSessionMapper?.invoke(it) ?: it })
        }
      }
    }

    /**
     * The final error of the deferred. [error] is the same exception that [SafeDeferred.await] wraps.
     */
    sealed interface MappedError : Unsuccessful {
      val error: Throwable
      fun throwWrapped(): Nothing
    }

    class Canceled(override val error: CancellationException) : MappedError {
      override fun throwWrapped(): Nothing {
        throw CancelledDeferred(error)
      }
    }

    class Failed(internal val unmappedError: Throwable, override val error: Throwable) : MappedError {
      override fun throwWrapped(): Nothing {
        throw FailedDeferred(error)
      }
    }
  }

  /**
   * Replaces [Deferred.isActive], [Deferred.isCompleted], [Deferred.isCancelled], [Deferred.getCompleted], [Deferred.getCompletionExceptionOrNull].
   *
   * Works better for code like `if (isFailed) getCompletionExceptionOrNull()!!`.
   *
   * When [deadSessionMapper] is set and the deferred fails, the state is [State.UnmappedError] until [await] or
   * [State.UnmappedError.await] maps the error.
   */
  @Suppress("UNCHECKED_CAST")
  @OptIn(ExperimentalCoroutinesApi::class)
  val state: State<T>
    get(): State<T> {
      if (!deferred.isCompleted) {
        return State.Active as State<T>
      }

      val unwrappedErr =
        deferred.getCompletionExceptionOrNull()
        ?: return State.Completed(deferred.getCompleted())

      while (true) {
        mappedErrorState.get()?.let {
          return it as State<T>
        }

        if (deadSessionMapper == null) {
          val result = convertException(unwrappedErr) { it }
          mappedErrorState.compareAndSet(null, result)
        }
        else {
          return State.UnmappedError(this, unwrappedErr) as State<T>
        }
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

  @ApiStatus.Experimental
  fun <B> map(block: (T) -> B): SafeDeferred<B> {
    val result = CompletableDeferred<B>()
    invokeWhenCompleted {
      when (it) {
        is State.Completed -> result.completeWith(runCatching { block(it.value) })
        is State.Canceled -> result.cancel(it.error)
        is State.Failed -> result.completeExceptionally(it.unmappedError)
        is State.UnmappedError -> result.completeExceptionally(it.unmappedError)
      }
    }
    // Preserve the dead-session mapping across the transformation so the mapped deferred still surfaces the canonical
    // exception instead of FailedDeferred.
    return SafeDeferred(result, mappedErrorState, deadSessionMapper)
  }

  private companion object {
    private inline fun convertException(
      initialErr: Throwable,
      deadSessionMapper: (Throwable) -> Throwable,
    ): State.MappedError {
      var err = initialErr
      while (true) {
        return when (err) {
          is CancelledDeferred -> {
            State.Canceled(err.cause)
          }

          is FailedDeferred -> {
            val cause = err.cause
            State.Failed(initialErr, deadSessionMapper(cause))
          }

          is CancellationException -> {
            val cause = err.cause
            if (cause == null) {
              State.Canceled(
                initialErr as? CancellationException
                ?: CancellationException(initialErr)
              )
            }
            else {
              err = cause
              continue
            }
          }

          else -> {
            State.Failed(initialErr, deadSessionMapper(err))
          }
        }
      }
    }
  }
}

/**
 * Just syntax sugar for the constructor of [SafeDeferred].
 * It may be helpful for preventing git history pollution when converting [Deferred] to [SafeDeferred].
 */
@ApiStatus.Experimental
fun <T> Deferred<T>.toSafeDeferred(deadSessionMapper: (suspend (Throwable) -> Throwable?)?): SafeDeferred<T> {
  return SafeDeferred(this, deadSessionMapper)
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

      null, is SafeDeferred.State.Unsuccessful -> SafeDeferred(coroutineScope.async {
        factory(key)
      })
    }
  }!!

@ApiStatus.Internal
fun <K, V> MutableMap<K, SafeDeferred<V>>.retrieveValidDeferred(
  key: K,
): SafeDeferred<V>? =
  compute(key) { _, old ->
    when (old?.state) {
      SafeDeferred.State.Active, is SafeDeferred.State.Completed -> old

      null, is SafeDeferred.State.Unsuccessful -> null
    }
  }