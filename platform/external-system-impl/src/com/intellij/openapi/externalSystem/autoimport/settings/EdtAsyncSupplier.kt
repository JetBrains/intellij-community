// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.autoimport.settings

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer

class EdtAsyncSupplier<R>(
  private val supplier: () -> R,
  private val shouldKeepTasksAsynchronous: () -> Boolean
) : AsyncSupplier<R> {
  override fun supply(consumer: (R) -> Unit, parentDisposable: Disposable) {
    val application = ApplicationManager.getApplication()
    if (shouldKeepTasksAsynchronous()) {
      application.invokeLater({ consumer(supplier()) }) {
        Disposer.isDisposed(parentDisposable)
      }
    }
    else {
      application.invokeAndWait { consumer(supplier()) }
    }
  }

  companion object {
    fun invokeOnEdt(shouldKeepTasksAsynchronous: () -> Boolean, action: () -> Unit, parentDisposable: Disposable) {
      EdtAsyncSupplier(action, shouldKeepTasksAsynchronous)
        .supply({}, parentDisposable)
    }
  }
}