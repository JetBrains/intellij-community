// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.autoimport.settings

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.runReadAction
import java.util.concurrent.Executor

abstract class ReadAsyncOperation<R>(private val backgroundExecutor: Executor, private vararg val equality: Any) : AsyncOperation<R> {
  override fun submit(callback: (R) -> Unit, parentDisposable: Disposable) {
    if (isBlocking()) {
      callback(runReadAction(::calculate))
    }
    else {
      ReadAction.nonBlocking<R> { calculate() }
        .expireWith(parentDisposable)
        .coalesceBy(*equality)
        .finishOnUiThread(ModalityState.defaultModalityState(), callback)
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
    ): ReadAsyncOperation<R> {
      return object : ReadAsyncOperation<R>(backgroundExecutor, equality) {
        override fun isBlocking() = isBlocking?.invoke() ?: super.isBlocking()
        override fun calculate() = action()
      }
    }
  }
}