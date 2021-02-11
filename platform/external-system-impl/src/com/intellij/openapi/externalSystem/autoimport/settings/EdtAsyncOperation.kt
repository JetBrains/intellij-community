// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.autoimport.settings

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer

abstract class EdtAsyncOperation<R> : AsyncOperation<R> {
  override fun submit(callback: (R) -> Unit, parentDisposable: Disposable) {
    val application = ApplicationManager.getApplication()
    if (isBlocking()) {
      application.invokeAndWait { callback(calculate()) }
    }
    else {
      application.invokeLater({ callback(calculate()) }) {
        Disposer.isDisposed(parentDisposable)
      }
    }
  }

  companion object {
    fun invokeOnEdt(isBlocking: () -> Boolean, action: () -> Unit, parentDisposable: Disposable) {
      val operation = object : EdtAsyncOperation<Any>() {
        override fun isBlocking() = isBlocking.invoke()
        override fun calculate() = action()
      }
      operation.submit({}, parentDisposable)
    }
  }
}