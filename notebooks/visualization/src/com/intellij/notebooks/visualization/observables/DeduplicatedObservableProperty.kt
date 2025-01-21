package com.intellij.notebooks.visualization.observables

import com.intellij.openapi.Disposable
import com.intellij.openapi.observable.dispatcher.SingleEventDispatcher
import com.intellij.openapi.observable.properties.ObservableMutableProperty

class DeduplicatedObservableProperty<T>(initialValue: T): ObservableMutableProperty<T> {

  private var value: T = initialValue

  private val changeDispatcher = SingleEventDispatcher.create<T>()

  private fun fireChangeEvent(oldValue: T, newValue: T) {
    if (oldValue != newValue) {
      changeDispatcher.fireEvent(newValue)
    }
  }

  override fun set(value: T) {
    val oldValue = this.value
    this.value = value
    fireChangeEvent(oldValue, value)
  }

  override fun get(): T {
    return this.value
  }

  override fun afterChange(parentDisposable: Disposable?, listener: (T) -> Unit): Unit =
    changeDispatcher.whenEventHappened(parentDisposable, listener)
}