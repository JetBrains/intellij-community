// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.observable.util

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.observable.dispatcher.SingleEventDispatcher
import com.intellij.openapi.observable.operation.core.ObservableOperationTrace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.CancellablePromise
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.await
import kotlin.time.Duration


suspend fun <R> Promise<R>.awaitPromise(timeout: Duration): R {
  return withContext(Dispatchers.EDT) {
    withTimeout(timeout) {
      await()
    }
  }
}

fun getPromise(parentDisposable: Disposable?, subscribe: (Disposable?, () -> Unit) -> Unit) =
  getPromise1(parentDisposable) { disposable, listener ->
    subscribe(disposable) { listener(null) }
  }

fun <T> getPromise1(parentDisposable: Disposable?, subscribe: (Disposable?, (T) -> Unit) -> Unit): Promise<T> {
  val promise = createPromise<T>(parentDisposable)
  subscribe(parentDisposable) {
    promise.setResult(it)
  }
  return promise
}

fun <A1, A2> getPromise2(parentDisposable: Disposable?, subscribe: (Disposable?, (A1, A2) -> Unit) -> Unit) =
  getPromise1(parentDisposable) { disposable, listener ->
    subscribe(disposable) { a1, a2 -> listener(a1 to a2) }
  }

fun <T> createPromise(parentDisposable: Disposable?): AsyncPromise<T> {
  return AsyncPromise<T>()
    .cancelWhenDisposed(parentDisposable)
}

fun <P : CancellablePromise<*>> P.cancelWhenDisposed(parentDisposable: Disposable?): P {
  parentDisposable?.whenDisposed {
    cancel()
  }
  return this
}
