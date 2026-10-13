// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.collaboration.ui

import com.intellij.util.EventDispatcher
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

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

fun <T> SingleValueModel<T>.asStateFlow(): StateFlow<T> {
  val flow = MutableStateFlow(value)
  addAndInvokeListener {
    flow.value = value
  }
  return flow
}

fun <T> SingleValueModel<T>.bindValueIn(scope: CoroutineScope, valueFlow: Flow<T>) {
  scope.launch {
    valueFlow.collect { newValue ->
      value = newValue
    }
  }
}