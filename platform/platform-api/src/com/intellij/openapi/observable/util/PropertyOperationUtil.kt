// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("PropertyOperationUtil")

package com.intellij.openapi.observable.util

import com.intellij.openapi.Disposable
import com.intellij.openapi.observable.properties.ObservableProperty

/**
 * Creates observable property that represents logic negation (!) operation for passed property.
 */
operator fun ObservableProperty<Boolean>.not(): ObservableProperty<Boolean> =
  transform { !it }

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

/**
 * Creates observable property that represents logic xor (^) operation between values of two passed properties.
 */
infix fun ObservableProperty<Boolean>.xor(property: ObservableProperty<Boolean>): ObservableProperty<Boolean> =
  operation(this, property) { l, r -> l xor r }

/**
 * Creates observable property that represents integer plus (+) operation between values of two passed properties.
 */
operator fun ObservableProperty<Int>.plus(property: ObservableProperty<Int>): ObservableProperty<Int> =
  operation(this, property) { l, r -> l + r }

/**
 * Creates observable property that represents integer minus (-) operation between values of two passed properties.
 */
operator fun ObservableProperty<Int>.minus(property: ObservableProperty<Int>): ObservableProperty<Int> =
  operation(this, property) { l, r -> l - r }

/**
 * Creates observable property that represents integer multiplying (*) operation between values of two passed properties.
 */
operator fun ObservableProperty<Int>.times(property: ObservableProperty<Int>): ObservableProperty<Int> =
  operation(this, property) { l, r -> l * r }

/**
 * Creates observable property that represents integer division (/) operation between values of two passed properties.
 */
operator fun ObservableProperty<Int>.div(property: ObservableProperty<Int>): ObservableProperty<Int> =
  operation(this, property) { l, r -> l / r }

/**
 * Creates boolean property that represents result of nullability check.
 */
fun <T> ObservableProperty<T>.isNull(): ObservableProperty<Boolean> =
  transform { it == null }

/**
 * Creates boolean property that represents result of non-nullability check.
 */
fun <T> ObservableProperty<T>.isNotNull(): ObservableProperty<Boolean> =
  transform { it != null }

/**
 * Creates observable property that represents custom operation between two properties.
 * @param operation defines operation between properties' values.
 */
fun <T1, T2, R> operation(
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
