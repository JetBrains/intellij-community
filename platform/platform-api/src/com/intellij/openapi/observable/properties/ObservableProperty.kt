// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.observable.properties

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

/**
 * Read only interface for observable property.
 */
interface ObservableProperty<T> : ReadOnlyProperty<Any?, T> {

  /**
   * @return value of property
   */
  fun get(): T

  /**
   * Subscribes on property change event.
   * @param listener is called when property is changed.
   * @param parentDisposable is used to early unsubscribe from property change events.
   */
  fun afterChange(parentDisposable: Disposable? = null, listener: (T) -> Unit) {
    logger<ObservableProperty<*>>().error("Please, implement this method directly.")
    when (parentDisposable) {
      null -> afterChange(listener)
      else -> afterChange(listener, parentDisposable)
    }
  }

  fun afterChange(listener: (T) -> Unit) = afterChange(null, listener)

  fun afterChange(listener: (T) -> Unit, parentDisposable: Disposable) = afterChange(parentDisposable, listener)

  /**
   * Value of Kotlin property can be delegated to ObservableProperty.
   *
   * For example:
   * ```
   * val property: ObservableProperty<T>
   * val value: T by property
   * ```
   *
   * See Also: https://kotlinlang.org/docs/delegated-properties.html
   */
  override fun getValue(thisRef: Any?, property: KProperty<*>): T = get()
}