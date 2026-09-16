// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Experimental

package com.intellij.collaboration.util

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.takeWhile
import org.jetbrains.annotations.ApiStatus

class IncrementallyComputedValue<T> private constructor(
  @PublishedApi
  internal val value: Value<T>?,
  private val complete: Boolean,
  private val loading: Boolean,
  private val exception: Exception?,
) {
  val isLoading: Boolean get() = loading
  val isComplete: Boolean get() = complete
  val isValueAvailable: Boolean get() = value != null
  val valueOrNull: T? get() = value?.value
  val exceptionOrNull: Exception? get() = exception

  companion object {
    fun <T> initial(): IncrementallyComputedValue<T> =
      IncrementallyComputedValue(
        value = null,
        complete = false,
        loading = false,
        exception = null
      )

    fun <T> loading(): IncrementallyComputedValue<T> =
      IncrementallyComputedValue(
        value = null,
        complete = false,
        loading = true,
        exception = null
      )

    fun <T> partialSuccess(value: T, loading: Boolean = true): IncrementallyComputedValue<T> =
      IncrementallyComputedValue(
        value = Value(value),
        complete = false,
        loading = loading,
        exception = null
      )

    fun <T> success(value: T): IncrementallyComputedValue<T> =
      IncrementallyComputedValue(
        value = Value(value),
        complete = true,
        loading = false,
        exception = null
      )

    fun <T> partialFailure(value: T, error: Exception): IncrementallyComputedValue<T> =
      IncrementallyComputedValue(
        value = Value(value),
        complete = false,
        loading = false,
        exception = error
      )

    fun <T> failure(error: Exception): IncrementallyComputedValue<T> =
      IncrementallyComputedValue(
        value = null,
        complete = false,
        loading = false,
        exception = error
      )

    fun <T> IncrementallyComputedValue<T>.complete(ifEmpty: () -> T): IncrementallyComputedValue<T> =
      IncrementallyComputedValue(
        value = value ?: Value(ifEmpty()),
        complete = true,
        loading = loading,
        exception = exception
      )

    fun <T> IncrementallyComputedValue<T>.withLoading(loading: Boolean): IncrementallyComputedValue<T> =
      IncrementallyComputedValue(
        value = value,
        complete = complete,
        loading = loading,
        exception = exception
      )

    fun <T> IncrementallyComputedValue<T>.appendPart(
      lastPart: Boolean,
      append: (T?) -> T,
    ): IncrementallyComputedValue<T> {
      val newValue = append(value?.value)
      return IncrementallyComputedValue(
        value = Value(newValue),
        complete = lastPart,
        loading = loading,
        exception = exception
      )
    }

    fun <T> IncrementallyComputedValue<T>.withException(exception: Exception): IncrementallyComputedValue<T> =
      IncrementallyComputedValue(
        value = value,
        complete = complete,
        loading = loading,
        exception = exception
      )
  }

  @PublishedApi
  @JvmInline
  internal value class Value<T>(val value: T)
}

inline fun <T> IncrementallyComputedValue<T>.onValueAvailable(consumer: (T) -> Unit): IncrementallyComputedValue<T> {
  if (value != null) {
    consumer(value.value)
  }
  return this
}

inline fun <T> IncrementallyComputedValue<T>.onNoValue(handler: () -> Unit): IncrementallyComputedValue<T> {
  if (value == null) {
    handler()
  }
  return this
}

suspend inline fun <T> Flow<List<T>>.collectIncrementallyTo(collector: FlowCollector<IncrementallyComputedValue<List<T>>>) {
  with(collector) {
    emit(IncrementallyComputedValue.loading())
    val batches = mutableListOf<List<T>>()
    try {
      collect { batch ->
        batches.add(batch)
        emit(IncrementallyComputedValue.partialSuccess(batches.flatten()))
      }
      if (batches.isNotEmpty()) {
        emit(IncrementallyComputedValue.success(batches.flatten()))
      }
    }
    catch (ce: CancellationException) {
      throw ce
    }
    catch (e: Exception) {
      if (batches.isNotEmpty()) {
        emit(IncrementallyComputedValue.partialFailure(batches.flatten(), e))
      }
      else {
        emit(IncrementallyComputedValue.failure(e))
      }
    }
  }
}

@ApiStatus.Internal
suspend fun <T> Flow<IncrementallyComputedValue<List<T>>>.consumeIncrementally(
  batchConsumer: (List<T>) -> Unit,
  onError: (Exception) -> Unit,
) {
  var counter = 0
  takeWhile { computedValue ->
    val items: List<T>? = computedValue.valueOrNull
    if (items != null && items.size > counter) {
      batchConsumer.invoke(items.subList(counter, items.size))
      counter = items.size
    }

    if (computedValue.isComplete) {
      return@takeWhile false
    }

    computedValue.exceptionOrNull?.let {
      onError(it)
      return@takeWhile false
    }

    true
  }.collect()
}