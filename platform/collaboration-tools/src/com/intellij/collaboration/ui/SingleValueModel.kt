// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.collaboration.ui

import com.intellij.collaboration.ui.SimpleEventListener
import com.intellij.openapi.Disposable
import com.intellij.util.EventDispatcher
import com.intellij.util.concurrency.annotations.RequiresEdt

open class SingleValueModel<T>(initialValue: T) {
  private val changeEventDispatcher = EventDispatcher.create(SimpleEventListener::class.java)

  var value: T = initialValue
    set(value) {
      field = value
      changeEventDispatcher.multicaster.eventOccurred()
    }

  @RequiresEdt
  fun addAndInvokeValueChangedListener(listener: () -> Unit) =
    SimpleEventListener.addAndInvokeListener(changeEventDispatcher, listener)

  @RequiresEdt
  fun addValueChangedListener(disposable: Disposable, listener: () -> Unit) =
    SimpleEventListener.addDisposableListener(changeEventDispatcher, disposable, listener)

  @RequiresEdt
  fun addValueChangedListener(listener: () -> Unit) =
    SimpleEventListener.addListener(changeEventDispatcher, listener)

  @RequiresEdt
  fun addListener(listener: (newValue: T) -> Unit) {
    SimpleEventListener.addListener(changeEventDispatcher) {
      listener(value)
    }
  }

  fun <R> map(mapper: (T) -> R): SingleValueModel<R> {
    val mappedModel = SingleValueModel(value.let(mapper))
    addValueChangedListener {
      mappedModel.value = value.let(mapper)
    }
    return mappedModel
  }

}