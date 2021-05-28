// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.autoimport.settings

import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.util.BackgroundTaskUtil

abstract class BackgroundAsyncSupplier<R> : AsyncSupplier<R> {
  override fun supply(consumer: (R) -> Unit, parentDisposable: Disposable) {
    if (isBlocking()) {
      consumer(get())
    }
    else {
      BackgroundTaskUtil.executeOnPooledThread(parentDisposable) {
        consumer(get())
      }
    }
  }
}