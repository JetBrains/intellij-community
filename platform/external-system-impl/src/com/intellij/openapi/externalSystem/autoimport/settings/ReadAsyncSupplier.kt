// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.autoimport.settings

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.progress.impl.CoreProgressManager
import java.util.concurrent.Executor

class ReadAsyncSupplier<R>(
  private val supplier: () -> R,
  private val shouldKeepTasksAsynchronous: () -> Boolean,
  private val equality: Array<out Any>,
  private val backgroundExecutor: Executor
) : AsyncSupplier<R> {
  override fun supply(consumer: (R) -> Unit, parentDisposable: Disposable) {
    if (shouldKeepTasksAsynchronous()) {
      ReadAction.nonBlocking(supplier)
        .expireWith(parentDisposable)
        .coalesceBy(*equality)
        .finishOnUiThread(ModalityState.defaultModalityState(), consumer)
        .submit(backgroundExecutor)
    }
    else {
      consumer(runReadAction(supplier))
    }
  }

  class Builder<R>(private val supplier: () -> R) {
    private var shouldKeepTasksAsynchronous: () -> Boolean =
      CoreProgressManager::shouldKeepTasksAsynchronous

    private var equality: Array<out Any> = emptyArray()

    fun shouldKeepTasksAsynchronous(provider: () -> Boolean) = apply {
      shouldKeepTasksAsynchronous = provider
    }

    fun coalesceBy(vararg equality: Any) = apply {
      this.equality = equality
    }

    fun build(backgroundExecutor: Executor) =
      ReadAsyncSupplier(supplier, shouldKeepTasksAsynchronous, equality, backgroundExecutor)
  }
}