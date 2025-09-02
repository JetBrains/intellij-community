// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.observable.util

import com.intellij.openapi.Disposable
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import org.jetbrains.annotations.ApiStatus
import kotlin.reflect.KMutableProperty0

@ApiStatus.Internal
fun <V> setKotlinProperty(property: KMutableProperty0<V>, value: V, disposable: Disposable) {
  val previousValue = property.get()
  property.set(value)
  disposable.whenDisposed {
    property.set(previousValue)
  }
}

@ApiStatus.Internal
fun setSystemProperty(key: String, value: String?, parentDisposable: Disposable) {
  val previousValue = System.getProperty(key)
  when (value) {
    null -> System.clearProperty(key)
    else -> System.setProperty(key, value)
  }
  parentDisposable.whenDisposed {
    when (previousValue) {
      null -> System.clearProperty(key)
      else -> System.setProperty(key, previousValue)
    }
  }
}

@ApiStatus.Internal
fun <T> setObservableProperty(property: ObservableMutableProperty<T>, value: T, parentDisposable: Disposable) {
  val previousValue = property.get()
  property.set(value)
  parentDisposable.whenDisposed {
    property.set(previousValue)
  }
}
