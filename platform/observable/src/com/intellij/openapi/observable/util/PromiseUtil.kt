// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.observable.util

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer.register
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.CancellablePromise
import org.jetbrains.concurrency.Promise

internal fun <T> getPromise(parentDisposable: Disposable, subscribe: (Disposable, (T) -> Unit) -> Unit): Promise<T> {
  val promise = AsyncPromise<T>()
    .cancelWhenDisposed(parentDisposable)
  subscribe(parentDisposable) {
    promise.setResult(it)
  }
  return promise
}

private fun <P : CancellablePromise<*>> P.cancelWhenDisposed(parentDisposable: Disposable?): P {
  if (parentDisposable != null) {
    register(parentDisposable, Disposable {
      this.cancel()
    })
  }
  return this
}

