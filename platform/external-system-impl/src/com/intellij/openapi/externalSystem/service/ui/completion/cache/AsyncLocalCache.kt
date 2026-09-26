// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.ui.completion.cache

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.completeWith
import kotlinx.coroutines.future.asCompletableFuture
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class AsyncLocalCache<T> {

  private val valueDeferredCache = BasicLocalCache<Deferred<T>>()
  private val valueCache = BasicLocalCache<T>()

  fun getValue(): T? {
    return valueCache.getValue()
  }

  suspend fun getOrCreateValue(stamp: Long, createValue: suspend () -> T): T {
    return getOrCreateValue(stamp, { createValue() }, { await() })
  }

  fun getOrCreateValueBlocking(stamp: Long, createValue: () -> T): T {
    return getOrCreateValue(stamp, { createValue() }, { asCompletableFuture().get() })
  }

  private inline fun getOrCreateValue(stamp: Long, createValue: () -> T, getValue: Deferred<T>.() -> T): T {
    val deferred = CompletableDeferred<T>()
    val valueDeferred = valueDeferredCache.getOrCreateValue(stamp) { deferred }
    if (valueDeferred === deferred) {
      deferred.completeWith(runCatching {
        createValue()
      })
    }
    val value = valueDeferred.getValue()
    return valueCache.getOrCreateValue(stamp) { value }
  }
}