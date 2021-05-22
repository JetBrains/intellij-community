// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.autoimport.settings

import com.intellij.openapi.Disposable
import org.jetbrains.concurrency.AsyncPromise
import java.util.concurrent.atomic.AtomicReference

abstract class MemoizedAsyncOperation<R> : BackgroundAsyncOperation<R>() {
  private val cache = AtomicReference<AsyncPromise<R>>()

  override fun submit(callback: (R) -> Unit, parentDisposable: Disposable) {
    cache.updateAndGet { promise ->
      promise ?: AsyncPromise<R>().apply {
        super.submit(::setResult, parentDisposable)
      }
    }.onSuccess(callback)
  }

  fun invalidate() {
    cache.set(null)
  }
}