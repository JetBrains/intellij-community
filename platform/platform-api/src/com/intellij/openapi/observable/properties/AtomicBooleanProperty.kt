// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.observable.properties

import java.util.concurrent.atomic.AtomicReference

/**
 * Atomic implementation of boolean property.
 */
class AtomicBooleanProperty(initial: Boolean) : AbstractObservableBooleanProperty(), AtomicMutableProperty<Boolean> {

  private val value = AtomicReference(initial)

  override fun get(): Boolean = value.get()

  override fun set() = set(true)

  override fun reset() = set(false)

  override fun set(value: Boolean) {
    val oldValue = this.value.getAndSet(value)
    fireChangeEvents(oldValue, value)
  }

  override fun updateAndGet(update: (Boolean) -> Boolean): Boolean {
    var oldValue: Boolean? = null
    val newValue = value.updateAndGet {
      oldValue = it
      update(it)
    }
    fireChangeEvents(oldValue!!, newValue)
    return newValue
  }

  fun compareAndSet(expect: Boolean, update: Boolean): Boolean {
    val succeed = value.compareAndSet(expect, update)
    if (succeed) {
      fireChangeEvents(expect, update)
    }
    return succeed
  }
}