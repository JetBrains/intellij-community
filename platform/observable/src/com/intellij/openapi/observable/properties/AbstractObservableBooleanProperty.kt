// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.observable.properties

import com.intellij.openapi.Disposable
import com.intellij.openapi.observable.dispatcher.SingleEventDispatcher
import org.jetbrains.annotations.ApiStatus

@ApiStatus.NonExtendable
abstract class AbstractObservableBooleanProperty :
  AbstractObservableProperty<Boolean>(),
  ObservableBooleanProperty {

  private val setDispatcher = SingleEventDispatcher.create()
  private val resetDispatcher = SingleEventDispatcher.create()

  protected fun fireChangeEvents(oldValue: Boolean, newValue: Boolean) {
    when {
      !oldValue && newValue -> fireSetEvent()
      oldValue && !newValue -> fireResetEvent()
    }
    fireChangeEvent(newValue)
  }

  private fun fireSetEvent() =
    setDispatcher.fireEvent()

  override fun afterSet(parentDisposable: Disposable?, listener: () -> Unit): Unit =
    setDispatcher.whenEventHappened(parentDisposable, listener)

  private fun fireResetEvent() =
    resetDispatcher.fireEvent()

  override fun afterReset(parentDisposable: Disposable?, listener: () -> Unit): Unit =
    resetDispatcher.whenEventHappened(parentDisposable, listener)
}