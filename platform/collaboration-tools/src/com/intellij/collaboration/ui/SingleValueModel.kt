// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.collaboration.ui

import com.intellij.util.EventDispatcher
import com.intellij.util.concurrency.annotations.RequiresEdt

class SingleValueModel<T>(initialValue: T) {
  private val changeEventDispatcher = EventDispatcher.create(SimpleEventListener::class.java)

  var value: T = initialValue
    set(value) {
      field = value
      changeEventDispatcher.multicaster.eventOccurred()
    }

  @RequiresEdt
  fun addAndInvokeListener(listener: (newValue: T) -> Unit) {
    addListener(listener)
    listener(value)
  }

  @RequiresEdt
  fun addListener(listener: (newValue: T) -> Unit) {
    SimpleEventListener.addListener(changeEventDispatcher) {
      listener(value)
    }
  }

  fun <R> map(mapper: (T) -> R): SingleValueModel<R> {
    val mappedModel = SingleValueModel(value.let(mapper))
    this.addListener {
      mappedModel.value = value.let(mapper)
    }
    return mappedModel
  }
}