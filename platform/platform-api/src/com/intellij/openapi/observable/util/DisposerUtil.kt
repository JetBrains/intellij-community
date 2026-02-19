// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.observable.util

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.atomic.AtomicBoolean

@ApiStatus.Internal
fun Disposable.whenDisposed(listener: () -> Unit) {
  Disposer.register(this, Disposable { listener() })
}

/**
 * This function allows to clean up [Disposer] from intermediate disposable.
 * It makes sense if [this] lifecycle is much more than [parentDisposable] lifecycle.
 *
 * @param listener will be executed only when [this] is disposed.
 * @param parentDisposable unsubscribes listener from [this] dispose events.
 *  So [listener] never be called when [parentDisposable] is disposed.
 */
@ApiStatus.Internal
@ApiStatus.Experimental
fun Disposable.whenDisposed(
  parentDisposable: Disposable,
  listener: () -> Unit
) {
  val isDisposed = AtomicBoolean(false)

  val disposable = Disposable {
    if (isDisposed.compareAndSet(false, true)) {
      listener()
    }
  }

  Disposer.register(this, disposable)

  Disposer.register(parentDisposable, Disposable {
    if (isDisposed.compareAndSet(false, true)) {
      Disposer.dispose(disposable)
    }
  })
}