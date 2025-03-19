// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Experimental
import java.util.concurrent.atomic.AtomicReference

/**
 * This is only a temporal API used to add reactivity possibilities into execution module until other options become available as a valid
 * dependency.
 * Do not use it. Use [com.intellij.openapi.observable.properties.ObservableProperty] instead.
 */
@ApiStatus.Internal
@Experimental
interface ReactiveProperty<T> {
  fun getValue(): T

  fun afterChange(parentDisposable: Disposable, listener: (T) -> Unit)
}

/**
 * This is only a temporal API used to add reactivity possibilities into execution module until other options become available as a valid
 * dependency.
 * Do not use it. Use [com.intellij.openapi.observable.properties.ObservableProperty] instead.
 */
@ApiStatus.Internal
@Experimental
class MutableReactiveProperty<T>(initialValue: T) : ReactiveProperty<T> {
  private val currentValue = AtomicReference<T>()
  private val changeEvent = Event<T>()

  override fun afterChange(parentDisposable: Disposable, listener: (T) -> Unit) {
    val subscription = Disposable { changeEvent -= listener }

    changeEvent += listener

    Disposer.register(parentDisposable, subscription)
  }

  override fun getValue(): T = currentValue.get()
  fun setValue(value: T) {
    if (value == currentValue.get()) return
    currentValue.set(value)
    changeEvent.observers.forEach { it(value) }
  }
}

private class Event<T> {
  private val listenersList = mutableListOf<(T) -> Unit>()
  val observers: List<(T) -> Unit> get() = listenersList

  operator fun plusAssign(observer: (T) -> Unit) {
    listenersList.add(observer)
  }

  operator fun minusAssign(observer: (T) -> Unit) {
    listenersList.remove(observer)
  }

  operator fun invoke(value: T) {
    for (observer in listenersList)
      observer(value)
  }
}