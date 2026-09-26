// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.kernel.rete

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CopyableThrowable

sealed class WithMatchResult<out T> {
  data class Success<T>(val value: T) : WithMatchResult<T>()
  data class Failure(val reason: CancellationReason) : WithMatchResult<Nothing>()
}

fun <T> WithMatchResult<T>.getOrThrow(): T =
  when (this) {
    is WithMatchResult.Failure -> throw UnsatisfiedMatchException(reason)
    is WithMatchResult.Success -> value
  }

fun <T> WithMatchResult<T>.getOrNull(): T? =
  when (this) {
    is WithMatchResult.Failure -> null
    is WithMatchResult.Success -> value
  }

inline fun <T, R> WithMatchResult<T>.map(transformer: (T) -> R): WithMatchResult<R> = flatMap { WithMatchResult.Success(transformer(it)) }

inline fun <T, R> WithMatchResult<T>.flatMap(transformer: (T) -> WithMatchResult<R>): WithMatchResult<R> =
  when (this) {
    is WithMatchResult.Failure -> this
    is WithMatchResult.Success -> transformer(value)
  }

fun <T> WithMatchResult<WithMatchResult<T>>.flatten(): WithMatchResult<T> = flatMap { it }

val <T> WithMatchResult<T>.isSuccess: Boolean get() = this is WithMatchResult.Success

val <T : Any> WithMatchResult<T>.successOrNull: T? get() = (this as? WithMatchResult.Success)?.value

class CancellationReason(val reason: String, val match: Match<*>?)

class UnsatisfiedMatchException(val reason: CancellationReason)
  : CancellationException(reason.reason),
    CopyableThrowable<UnsatisfiedMatchException> {

  override var cause: Throwable? = super.cause
    private set

  override fun createCopy(): UnsatisfiedMatchException {
    return UnsatisfiedMatchException(reason).also { it.cause = this }
  }
}

/**
 * A match is owned by the coroutine that receives it from its subscription, and it will only be properly invalidated in that context.
 */
class SubscriptionDisposedException(val m: Match<*>) : CancellationException("Owner of this match has been disposed."), CopyableThrowable<SubscriptionDisposedException> {
  // it should be RuntimeException, but because cancellation is not atomic
  // and matches migth travel through channels (debounce, conflate, etc),
  // consumer might get a match from an already dead producer and there is nothing we can do
  override var cause: Throwable? = super.cause
    private set

  override fun createCopy(): SubscriptionDisposedException {
    return SubscriptionDisposedException(m).also { it.cause = this }
  }
}
