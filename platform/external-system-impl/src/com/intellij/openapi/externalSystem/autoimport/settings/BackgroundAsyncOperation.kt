// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.autoimport.settings

import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.util.BackgroundTaskUtil

abstract class BackgroundAsyncOperation<R> : AsyncOperation<R> {
  override fun submit(callback: (R) -> Unit, parentDisposable: Disposable) {
    if (isBlocking()) {
      callback(calculate())
    }
    else {
      BackgroundTaskUtil.executeOnPooledThread(parentDisposable) {
        callback(calculate())
      }
    }
  }
}