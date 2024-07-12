// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.util

import kotlinx.coroutines.flow.FlowCollector
import java.util.concurrent.CancellationException

/**
 * Represents the state of computing some value of type [T]
 *
 * null value means that the computation hasn't completed yet
 */
@JvmInline
value class ComputedResult<out T> internal constructor(
  val result: Result<T>?
) {
  val isSuccess: Boolean get() = result != null && result.isSuccess
  val isInProgress: Boolean get() = result == null

  companion object {
    fun <T> loading(): ComputedResult<T> = ComputedResult(null)
    fun <T> success(value: T): ComputedResult<T> = ComputedResult(Result.success(value))
    fun <T> failure(error: Throwable): ComputedResult<T> = ComputedResult(Result.failure(error))

    inline fun <T> compute(computer: () -> T): ComputedResult<T>? =
      try {
        val result = computer()
        success(result)
      }
      catch (ce: CancellationException) {
        null
      }
      catch (e: Exception) {
        failure(e)
      }
  }
}

fun <T> ComputedResult<T>.getOrNull(): T? = result?.getOrNull()

fun ComputedResult<*>.exceptionOrNull(): Throwable? = result?.exceptionOrNull()

fun <R, T> ComputedResult<T>.fold(onInProgress: () -> R, onSuccess: (value: T) -> R, onFailure: (exception: Throwable) -> R): R =
  result?.fold(onSuccess, onFailure) ?: onInProgress()

inline fun <T> ComputedResult<T>.onSuccess(consumer: (T) -> Unit): ComputedResult<T> = apply {
  result?.onSuccess(consumer)
}

inline fun <T> ComputedResult<T>.onFailure(consumer: (Throwable) -> Unit): ComputedResult<T> = apply {
  result?.onFailure(consumer)
}

inline fun <T> ComputedResult<T>.onInProgress(consumer: () -> Unit): ComputedResult<T> = apply {
  if (isInProgress) consumer()
}


fun <T, R> ComputedResult<T>.map(mapper: (value: T) -> R): ComputedResult<R> = ComputedResult(result?.map(mapper))

/**
 * Compute the result via [computer] and publish computation states to the collector
 */
suspend inline fun <T> FlowCollector<ComputedResult<T>?>.computeEmitting(computer: () -> T): ComputedResult<T>? {
  emit(ComputedResult.loading())
  return ComputedResult.compute(computer).also {
    emit(it)
  }
}