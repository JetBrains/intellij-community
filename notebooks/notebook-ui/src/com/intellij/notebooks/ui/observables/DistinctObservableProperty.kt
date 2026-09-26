// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notebooks.ui.observables

import com.intellij.openapi.Disposable
import com.intellij.openapi.observable.dispatcher.SingleEventDispatcher
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.observable.properties.ObservableProperty

private val UNDEFINED = Any()

@Suppress("UNCHECKED_CAST")
abstract class BaseDistinctObservableProperty<T, P : ObservableProperty<T>>(
  protected val origin: P,
  private val equality: (T, T) -> Boolean,
) : ObservableProperty<T> {

  private var value: T = UNDEFINED as T

  private val changeDispatcher = SingleEventDispatcher.create<T>()

  init {
    origin.afterChange {
      if (value == UNDEFINED || !equality(value, it)) {
        value = it
        fireChangeEvent(it)
      }
    }
  }

  private fun fireChangeEvent(newValue: T) {
    changeDispatcher.fireEvent(newValue)
  }

  override fun get(): T {
    return origin.get()
  }

  override fun afterChange(parentDisposable: Disposable?, listener: (T) -> Unit): Unit =
    changeDispatcher.whenEventHappened(parentDisposable, listener)
}

class DistinctObservableProperty<T>(
  origin: ObservableProperty<T>,
  equality: (T, T) -> Boolean,
) : BaseDistinctObservableProperty<T, ObservableProperty<T>>(origin, equality) {}

class DistinctObservableMutableProperty<T>(
  origin: ObservableMutableProperty<T>,
  equality: (T, T) -> Boolean,
) : BaseDistinctObservableProperty<T, ObservableMutableProperty<T>>(origin, equality), ObservableMutableProperty<T> {
  override fun set(value: T) {
    origin.set(value)
  }
}

fun <T> ObservableProperty<T>.distinct(): ObservableProperty<T> =
  DistinctObservableProperty<T>(this) { v1, v2 -> v1 == v2 }

fun <T> ObservableMutableProperty<T>.distinct(): ObservableMutableProperty<T> =
  DistinctObservableMutableProperty<T>(this) { v1, v2 -> v1 == v2 }

fun <T> ObservableMutableProperty<T>.distinct(equality: (T, T) -> Boolean): ObservableMutableProperty<T> =
  DistinctObservableMutableProperty<T>(this, equality)