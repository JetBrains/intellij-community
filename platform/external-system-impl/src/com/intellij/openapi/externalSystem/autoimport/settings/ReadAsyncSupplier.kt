// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.autoimport.settings

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.runReadAction
import java.util.concurrent.Executor

abstract class ReadAsyncSupplier<R>(private val backgroundExecutor: Executor, private vararg val equality: Any) : AsyncSupplier<R> {
  override fun supply(consumer: (R) -> Unit, parentDisposable: Disposable) {
    if (isBlocking()) {
      consumer(runReadAction(this::get))
    }
    else {
      ReadAction.nonBlocking<R> { get() }
        .expireWith(parentDisposable)
        .coalesceBy(*equality)
        .finishOnUiThread(ModalityState.defaultModalityState(), consumer)
        .submit(backgroundExecutor)
    }
  }

  companion object {
    fun <R> readAction(action: () -> R, backgroundExecutor: Executor, vararg equality: Any) =
      readAction(null, action, backgroundExecutor, equality)

    fun <R> readAction(
      isBlocking: (() -> Boolean)?,
      action: () -> R,
      backgroundExecutor: Executor,
      vararg equality: Any
    ): ReadAsyncSupplier<R> {
      return object : ReadAsyncSupplier<R>(backgroundExecutor, equality) {
        override fun isBlocking() = isBlocking?.invoke() ?: super.isBlocking()
        override fun get() = action()
      }
    }
  }
}