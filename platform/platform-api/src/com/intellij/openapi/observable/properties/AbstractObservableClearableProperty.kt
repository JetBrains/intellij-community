// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.observable.properties

import com.intellij.openapi.Disposable
import com.intellij.util.containers.DisposableWrapperList
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Suppress("DEPRECATION")
abstract class AbstractObservableClearableProperty<T> : AbstractObservableProperty<T>(), ObservableClearableProperty<T> {

  private val resetListeners = DisposableWrapperList<() -> Unit>()

  protected fun fireResetEvent() {
    resetListeners.forEach { it() }
  }

  override fun afterReset(listener: () -> Unit) {
    resetListeners.add(listener)
  }

  override fun afterReset(listener: () -> Unit, parentDisposable: Disposable) {
    resetListeners.add(listener, parentDisposable)
  }
}