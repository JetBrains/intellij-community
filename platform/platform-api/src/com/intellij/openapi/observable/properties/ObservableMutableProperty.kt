// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.observable.properties

import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

interface ObservableMutableProperty<T> : ReadWriteProperty<Any?, T>, ObservableProperty<T> {

  /**
   * Sets value of property.
   */
  fun set(value: T)

  /**
   * Value of Kotlin mutable property can be delegated to ObservableMutableProperty.
   * For example:
   * ```
   * val property: ObservableMutableProperty<T>
   * var value: T by property
   * ```
   * See Also: https://kotlinlang.org/docs/delegated-properties.html
   */
  override fun getValue(thisRef: Any?, property: KProperty<*>): T = get()

  override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) = set(value)
}