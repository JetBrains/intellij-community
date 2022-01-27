// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("PropertyOperationUtil")

package com.intellij.openapi.observable.util

import com.intellij.openapi.Disposable
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.observable.properties.ObservableProperty
import com.intellij.openapi.ui.getCanonicalPath
import com.intellij.openapi.ui.getPresentablePath
import com.intellij.openapi.util.NlsSafe

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
 * Creates observable property that represents result of check on equals.
 */
infix fun <T> ObservableProperty<T>.equalsTo(value: T): ObservableProperty<Boolean> =
  transform { it == value }

/**
 * Creates observable property that represents result of negated check on equals.
 */
infix fun <T> ObservableProperty<T>.notEqualsTo(value: T): ObservableProperty<Boolean> =
  transform { it != value }

/**
 * Creates observable property that represents force non-null value.
 */
fun <T : Any> ObservableProperty<T?>.notNull(): ObservableProperty<T> =
  transform { it!! }

/**
 * Creates boolean property that represents result of nullability check.
 */
fun <T : Any> ObservableProperty<T?>.isNull(): ObservableProperty<Boolean> =
  equalsTo(null)

/**
 * Creates boolean property that represents result of non-nullability check.
 */
fun <T : Any> ObservableProperty<T?>.isNotNull(): ObservableProperty<Boolean> =
  notEqualsTo(null)

/**
 * Creates observable property that represents property with trimmed value.
 */
fun ObservableProperty<String>.trim(): ObservableProperty<String> =
  transform(String::trim)

/**
 * Creates observable mutable property that represents property with trimmed value.
 */
fun ObservableMutableProperty<String>.trim(): ObservableMutableProperty<String> =
  transform(String::trim, String::trim)

/**
 * Creates observable property that represents property with presentable path value.
 */
fun ObservableProperty<String>.toUiPathProperty(): ObservableProperty<@NlsSafe String> =
  transform(::getPresentablePath)

/**
 * Creates observable mutable property that represents property with presentable path value.
 * Note: Value of source property will be canonical.
 */
fun ObservableMutableProperty<String>.toUiPathProperty(): ObservableMutableProperty<@NlsSafe String> =
  transform(::getPresentablePath, ::getCanonicalPath)

/**
 * Creates observable property that represents property with transformed value.
 * @param map is value transformation function
 */
fun <T, R> ObservableProperty<T>.transform(map: (T) -> R): ObservableProperty<R> =
  object : ObservablePropertyTransformation<T, R>() {
    override val property = this@transform
    override fun map(value: T) = map(value)
  }

/**
 * Creates observable mutable property that represents property with transformed value.
 * @param map is value forward transformation function
 * @param comap is value backward transformation function
 */
fun <T, R> ObservableMutableProperty<T>.transform(map: (T) -> R, comap: (R) -> T): ObservableMutableProperty<R> =
  object : ObservableMutablePropertyTransformation<T, R>() {
    override val property = this@transform
    override fun map(value: T) = map(value)
    override fun comap(value: R) = comap(value)
  }

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

private abstract class ObservableMutablePropertyTransformation<T, R> :
  ObservablePropertyTransformation<T, R>(),
  ObservableMutableProperty<R> {

  abstract override val property: ObservableMutableProperty<T>

  protected abstract fun comap(value: R): T

  override fun set(value: R) =
    property.set(comap(value))
}

private abstract class ObservablePropertyTransformation<T, R> : ObservableProperty<R> {

  protected abstract val property: ObservableProperty<T>

  protected abstract fun map(value: T): R

  override fun get(): R =
    map(property.get())

  override fun afterChange(listener: (R) -> Unit) =
    property.afterChange { listener(map(it)) }

  override fun afterChange(listener: (R) -> Unit, parentDisposable: Disposable) =
    property.afterChange({ listener(map(it)) }, parentDisposable)

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    return property == other
  }

  override fun hashCode(): Int {
    return property.hashCode()
  }
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
