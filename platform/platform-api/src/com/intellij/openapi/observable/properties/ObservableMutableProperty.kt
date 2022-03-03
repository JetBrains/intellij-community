// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.observable.properties

import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

interface ObservableMutableProperty<T> : ReadWriteProperty<Any?, T>, ObservableProperty<T> {

  fun set(value: T)

  override fun getValue(thisRef: Any?, property: KProperty<*>): T = get()

  override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) = set(value)
}