// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.observable.properties

import com.intellij.openapi.Disposable
import java.util.concurrent.atomic.AtomicReference

/**
 * Atomic implementation of boolean property.
 */
@Suppress("DEPRECATION")
class AtomicBooleanProperty(
  initial: Boolean
) : AbstractObservableBooleanProperty(),
    AtomicMutableBooleanProperty,
    BooleanProperty {

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

  override fun getAndSet(newValue: Boolean): Boolean {
    val oldValue = value.getAndSet(newValue)
    fireChangeEvents(oldValue, newValue)
    return oldValue
  }

  fun compareAndSet(expect: Boolean, update: Boolean): Boolean {
    val succeed = value.compareAndSet(expect, update)
    if (succeed) {
      fireChangeEvents(expect, update)
    }
    return succeed
  }

  //TODO: Remove with BooleanProperty
  override fun afterSet(listener: () -> Unit) = afterSet(null, listener)
  override fun afterSet(listener: () -> Unit, parentDisposable: Disposable) = afterSet(parentDisposable, listener)
  override fun afterReset(listener: () -> Unit) = afterReset(null, listener)
  override fun afterReset(listener: () -> Unit, parentDisposable: Disposable) = afterReset(parentDisposable, listener)
}