// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.autoimport.settings

import com.intellij.openapi.Disposable
import org.jetbrains.concurrency.AsyncPromise
import java.util.concurrent.atomic.AtomicReference

class CachingAsyncSupplier<R>(private val supplier: AsyncSupplier<R>) : AsyncSupplier<R> {

  private val cache = AtomicReference<AsyncPromise<R>>()

  override fun supply(parentDisposable: Disposable, consumer: (R) -> Unit) {
    cache.updateAndGet { promise ->
      promise ?: AsyncPromise<R>().apply {
        supplier.supply(parentDisposable) {
          setResult(it)
        }
      }
    }.onSuccess(consumer)
  }

  fun invalidate() {
    cache.set(null)
  }
}