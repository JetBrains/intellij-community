// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.autoimport.settings

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer

abstract class EdtAsyncSupplier<R> : AsyncSupplier<R> {
  override fun supply(consumer: (R) -> Unit, parentDisposable: Disposable) {
    val application = ApplicationManager.getApplication()
    if (isBlocking()) {
      application.invokeAndWait { consumer(get()) }
    }
    else {
      application.invokeLater({ consumer(get()) }) {
        Disposer.isDisposed(parentDisposable)
      }
    }
  }

  companion object {
    fun invokeOnEdt(isBlocking: () -> Boolean, action: () -> Unit, parentDisposable: Disposable) {
      val operation = object : EdtAsyncSupplier<Any>() {
        override fun isBlocking() = isBlocking.invoke()
        override fun get() = action()
      }
      operation.supply({}, parentDisposable)
    }
  }
}