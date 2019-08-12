// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("DisposeUtils")

package com.intellij.openapi.externalSystem.util

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.atomic.AtomicBoolean

@ApiStatus.Internal
fun createOrDisposable(vararg parents: Disposable): Disposable {
  val isDisposed = AtomicBoolean(false)
  val disposable = Disposer.newDisposable()
  for (parent in parents) {
    Disposer.register(parent, Disposable {
      if (!isDisposed.getAndSet(true)) {
        Disposer.dispose(disposable)
      }
    })
  }
  return disposable
}