// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.observable.properties

import com.intellij.openapi.Disposable
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

interface ObservableProperty<T> : ReadOnlyProperty<Any?, T> {
  fun get(): T

  fun afterChange(listener: (T) -> Unit)

  fun afterChange(listener: (T) -> Unit, parentDisposable: Disposable)

  override fun getValue(thisRef: Any?, property: KProperty<*>): T = get()
}