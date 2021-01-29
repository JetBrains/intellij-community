// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.autoimport.settings

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import java.util.concurrent.Executor

class ReadAsyncOperation<R>(
  private val backgroundExecutor: Executor,
  private val calculate: () -> R,
  private val parentDisposable: Disposable
) : AsyncOperation<R> {

  override fun submit(callback: (R) -> Unit) {
    if (ApplicationManager.getApplication().isHeadlessEnvironment) {
      callback(calculate())
    }
    else {
      ReadAction.nonBlocking<R> { calculate() }
        .expireWith(parentDisposable)
        .coalesceBy(this)
        .finishOnUiThread(ModalityState.defaultModalityState(), callback)
        .submit(backgroundExecutor)
    }
  }
}