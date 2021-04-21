// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui.codereview

import com.intellij.util.EventDispatcher
import java.util.*

class SingleValueModelImpl<T>(initialValue: T) : SingleValueModel<T> {
  private val listeners = EventDispatcher.create(ValueChangedListener::class.java)

  override var value: T = initialValue
    set(value) {
      field = value
      listeners.multicaster.valueUpdated(value)
    }

  override fun addValueUpdatedListener(listener: (newValue: T) -> Unit) {
    listeners.addListener(object : ValueChangedListener {
      override fun valueUpdated(newValue: Any?) {
        @Suppress("UNCHECKED_CAST")
        listener(newValue as T)
      }
    })
  }

  private interface ValueChangedListener : EventListener {
    fun valueUpdated(newValue: Any?)
  }
}