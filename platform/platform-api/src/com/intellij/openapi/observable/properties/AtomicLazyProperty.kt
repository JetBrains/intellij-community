// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.observable.properties

import java.util.concurrent.atomic.AtomicReference

/**
 * Atomic implementation of lazy property.
 * @param initial will be called only when methods [get] and [updateAndGet] is called.
 */
open class AtomicLazyProperty<T>(private val initial: () -> T) : AbstractObservableClearableProperty<T>(), AtomicMutableProperty<T> {

  private val value = AtomicReference<Any?>(UNINITIALIZED_VALUE)

  override fun get(): T {
    return update { it }
  }

  override fun set(value: T) {
    this.value.set(value)
    fireChangeEvent(value)
  }

  override fun updateAndGet(update: (T) -> T): T {
    val newValue = update(update)
    fireChangeEvent(newValue)
    return newValue
  }

  @Suppress("UNCHECKED_CAST")
  private fun update(update: (T) -> T): T {
    val resolve = { it: Any? -> if (it === UNINITIALIZED_VALUE) initial() else it as T }
    return value.updateAndGet { update(resolve(it)) } as T
  }

  override fun reset() {
    value.set(UNINITIALIZED_VALUE)
    fireResetEvent()
  }

  companion object {
    private val UNINITIALIZED_VALUE = Any()
  }
}