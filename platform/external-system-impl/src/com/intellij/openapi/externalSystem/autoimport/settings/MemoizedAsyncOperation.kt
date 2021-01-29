// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.autoimport.settings

import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.util.BackgroundTaskUtil
import org.jetbrains.concurrency.AsyncPromise
import java.util.concurrent.atomic.AtomicReference

abstract class MemoizedAsyncOperation<R>(private val parentDisposable: Disposable) : AsyncOperation<R> {

  protected abstract fun calculate(): R

  protected abstract fun isAsyncAllowed(): Boolean

  private val cache = AtomicReference<AsyncPromise<R>>()

  override fun submit(callback: (R) -> Unit) {
    cache.updateAndGet { promise ->
      promise ?: AsyncPromise<R>().apply {
        executeOnPooledThread {
          setResult(calculate())
        }
      }
    }.onSuccess(callback)
  }

  private fun executeOnPooledThread(action: () -> Unit) {
    if (isAsyncAllowed()) {
      BackgroundTaskUtil.executeOnPooledThread(parentDisposable, action)
    }
    else {
      action()
    }
  }

  fun invalidate() {
    cache.set(null)
  }
}