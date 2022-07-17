// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.observable.properties

import com.intellij.openapi.Disposable
import com.intellij.util.containers.DisposableWrapperList
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
abstract class AbstractObservableProperty<T> : ObservableProperty<T> {

  private val changeListeners = DisposableWrapperList<(T) -> Unit>()

  protected fun fireChangeEvent(value: T) {
    changeListeners.forEach { it(value) }
  }

  override fun afterChange(listener: (T) -> Unit) {
    changeListeners.add(listener)
  }

  override fun afterChange(listener: (T) -> Unit, parentDisposable: Disposable) {
    changeListeners.add(listener, parentDisposable)
  }
}