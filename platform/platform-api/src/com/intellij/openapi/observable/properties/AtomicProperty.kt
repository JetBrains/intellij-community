// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.observable.properties

import java.util.concurrent.atomic.AtomicReference

/**
 * Simple atomic property implementation.
 */
class AtomicProperty<T>(private val initialValue: T) : AbstractObservableClearableProperty<T>(), AtomicMutableProperty<T> {
  private val reference = AtomicReference(initialValue)

  override fun get(): T {
    return reference.get()
  }

  override fun set(value: T) {
    reference.set(value)
    fireChangeEvent(value)
  }

  override fun updateAndGet(update: (T) -> T): T {
    val newValue = reference.updateAndGet(update)
    fireChangeEvent(newValue)
    return newValue
  }

  override fun reset() {
    reference.set(initialValue)
    fireResetEvent()
  }
}