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
  internal val complete: Boolean,
  internal val exception: Exception?,
) {
  val isLoading: Boolean get() = !complete && exception == null
  val isComplete: Boolean get() = complete
  val isValueAvailable: Boolean get() = value != null
  val valueOrNull: T? get() = value?.value
  val exceptionOrNull: Exception? get() = exception

  companion object {
    fun <T> loading(): IncrementallyComputedValue<T> =
      IncrementallyComputedValue(null, false, null)

    fun <T> partialSuccess(value: T): IncrementallyComputedValue<T> =
      IncrementallyComputedValue(Value(value), false, null)

    fun <T> success(value: T): IncrementallyComputedValue<T> =
      IncrementallyComputedValue(Value(value), true, null)

    fun <T> partialFailure(value: T, error: Exception): IncrementallyComputedValue<T> =
      IncrementallyComputedValue(Value(value), false, error)

    fun <T> failure(error: Exception): IncrementallyComputedValue<T> =
      IncrementallyComputedValue(null, false, error)
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