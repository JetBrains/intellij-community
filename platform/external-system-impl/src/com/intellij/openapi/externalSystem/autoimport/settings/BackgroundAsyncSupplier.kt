// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.autoimport.settings

import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.impl.CoreProgressManager
import com.intellij.openapi.progress.util.BackgroundTaskUtil
import java.util.concurrent.Executor

class BackgroundAsyncSupplier<R>(
  private val supplier: AsyncSupplier<R>,
  private val shouldKeepTasksAsynchronous: () -> Boolean,
  private val backgroundExecutor: Executor
) : AsyncSupplier<R> {
  override fun supply(consumer: (R) -> Unit, parentDisposable: Disposable) {
    if (shouldKeepTasksAsynchronous()) {
      BackgroundTaskUtil.execute(backgroundExecutor, parentDisposable) {
        supplier.supply(consumer, parentDisposable)
      }
    }
    else {
      supplier.supply(consumer, parentDisposable)
    }
  }

  class Builder<R>(private val supplier: AsyncSupplier<R>) {
    constructor(supplier: () -> R) : this(AsyncSupplier.blocking(supplier))

    private var shouldKeepTasksAsynchronous: () -> Boolean =
      CoreProgressManager::shouldKeepTasksAsynchronous

    fun shouldKeepTasksAsynchronous(provider: () -> Boolean) = apply {
      shouldKeepTasksAsynchronous = provider
    }

    fun build(backgroundExecutor: Executor) =
      BackgroundAsyncSupplier(supplier, shouldKeepTasksAsynchronous, backgroundExecutor)
  }
}