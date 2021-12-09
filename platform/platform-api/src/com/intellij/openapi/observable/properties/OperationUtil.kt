// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("OperationUtil")

package com.intellij.openapi.observable.properties

import com.intellij.openapi.Disposable

/**
 * Creates observable property that represents logic negation (!) operation for passed property.
 */
operator fun ObservableProperty<Boolean>.not(): ObservableProperty<Boolean> =
  operation(this) { !it }

/**
 * Creates observable property that represents logic and (&&) operation between values of two passed properties.
 */
infix fun ObservableProperty<Boolean>.and(property: ObservableProperty<Boolean>): ObservableProperty<Boolean> =
  operation(this, property) { l, r -> l && r }

/**
 * Creates observable property that represents logic or (||) operation between values of two passed properties.
 */
infix fun ObservableProperty<Boolean>.or(property: ObservableProperty<Boolean>): ObservableProperty<Boolean> =
  operation(this, property) { l, r -> l || r }

private fun <T, R> operation(
  property: ObservableProperty<T>,
  operation: (T) -> R
): ObservableProperty<R> =
  object : AbstractDelegateObservableProperty<R>(property) {
    override fun get(): R = operation(property.get())
  }

private fun <T1, T2, R> operation(
  left: ObservableProperty<T1>,
  right: ObservableProperty<T2>,
  operation: (T1, T2) -> R
): ObservableProperty<R> =
  object : AbstractDelegateObservableProperty<R>(left, right) {
    override fun get(): R = operation(left.get(), right.get())
  }

private abstract class AbstractDelegateObservableProperty<T>(
  private val properties: List<ObservableProperty<*>>
) : ObservableProperty<T> {

  constructor(vararg properties: ObservableProperty<*>) : this(properties.toList())

  override fun afterChange(listener: (T) -> Unit) {
    for (property in properties) {
      property.afterChange { listener(get()) }
    }
  }

  override fun afterChange(listener: (T) -> Unit, parentDisposable: Disposable) {
    for (property in properties) {
      property.afterChange({ listener(get()) }, parentDisposable)
    }
  }
}