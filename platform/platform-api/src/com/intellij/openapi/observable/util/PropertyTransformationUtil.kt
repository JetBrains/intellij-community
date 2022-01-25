// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("PropertyTransformationUtil")

package com.intellij.openapi.observable.util

import com.intellij.openapi.Disposable
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.observable.properties.ObservableProperty
import com.intellij.openapi.ui.getCanonicalPath
import com.intellij.openapi.ui.getPresentablePath
import com.intellij.openapi.util.NlsSafe

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