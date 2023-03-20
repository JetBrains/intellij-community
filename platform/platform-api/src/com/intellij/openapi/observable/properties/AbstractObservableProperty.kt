// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.observable.properties

import com.intellij.openapi.Disposable
import com.intellij.openapi.observable.dispatcher.SingleEventDispatcher
import org.jetbrains.annotations.ApiStatus

@ApiStatus.NonExtendable
abstract class AbstractObservableProperty<T> : ObservableProperty<T> {

  private val changeDispatcher = SingleEventDispatcher.create<T>()

  protected fun fireChangeEvent(value: T) =
    changeDispatcher.fireEvent(value)

  override fun afterChange(parentDisposable: Disposable?, listener: (T) -> Unit) =
    changeDispatcher.whenEventHappened(parentDisposable, listener)
}