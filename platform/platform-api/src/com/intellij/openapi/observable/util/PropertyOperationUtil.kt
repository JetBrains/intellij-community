// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("PropertyOperationUtil")

package com.intellij.openapi.observable.util

import com.intellij.openapi.Disposable
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.observable.properties.ObservableProperty
import com.intellij.openapi.ui.getCanonicalPath
import com.intellij.openapi.ui.getPresentablePath
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.SystemDependent
import java.io.File

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
 * Note: Value of source property must be canonical.
 */
fun ObservableProperty<String>.toUiPathProperty(): ObservableProperty<@NlsSafe String> =
  transform(::getPresentablePath)

/**
 * Creates observable mutable property that represents property with presentable path value.
 * Note: Value of source property must be canonical.
 */
fun ObservableMutableProperty<String>.toUiPathProperty(): ObservableMutableProperty<@NlsSafe String> =
  transform(::getPresentablePath, ::getCanonicalPath)

@Suppress("DeprecatedCallableAddReplaceWith")
@Deprecated(message = "use joinCanonicalPath instead")
fun ObservableProperty<@SystemDependent String>.joinSystemDependentPath(vararg properties: ObservableProperty<@SystemDependent String>): ObservableProperty<String> =
  operation(this, *properties) { it.joinToString(File.separator) }

/**
 * Creates observable property that represents property with joined canonical path value.
 * Note: Value of source properties must be canonical.
 */
fun ObservableProperty<@NlsSafe String>.joinCanonicalPath(vararg properties: ObservableProperty<@NlsSafe String>): ObservableProperty<String> =
  operation(this, *properties) { it.joinToString("/") }

/**
 * Creates mutable property string view for int property.
 * All non-int string values will be ignored.
 */
fun ObservableMutableProperty<Int>.toStringIntProperty(): ObservableMutableProperty<String> =
  toStringProperty { it.toIntOrNull() }

/**
 * Creates mutable property string view for boolean property.
 * All non-boolean string values will be ignored.
 */
fun ObservableMutableProperty<Boolean>.toStringBooleanProperty(): ObservableMutableProperty<String> =
  toStringProperty { it.toBooleanStrictOrNull() }

/**
 * Creates mutable property string view for enum property.
 * All non-enum string values will be ignored.
 */
inline fun <reified T : Enum<T>> ObservableMutableProperty<T>.toStringEnumProperty(): ObservableMutableProperty<String> =
  toStringProperty { it.toEnumOrNull<T>() }

/**
 * Creates observable property string view for value property.
 */
fun <T : Any> ObservableProperty<T>.toStringProperty(): ObservableProperty<String> =
  transform { it.toString() }

/**
 * Creates mutable property string view for value property.
 * All non-deserializable string values will be ignored.
 */
fun <T : Any> ObservableMutableProperty<T>.toStringProperty(deserialize: (String) -> T?): ObservableMutableProperty<String> =
  toStringProperty({ it.toString() }, deserialize)

/**
 * Creates mutable property string view for value property.
 * All non-deserializable string values will be ignored.
 */
fun <T : Any> ObservableMutableProperty<T>.toStringProperty(
  serialize: (T?) -> String,
  deserialize: (String) -> T?
): ObservableMutableProperty<String> =
  transform<T, T?>({ it }, { it!! })
    .backwardFilter { it != null }
    .transform(serialize, deserialize)

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
 * @param backwardMap is value backward transformation function
 */
fun <T, R> ObservableMutableProperty<T>.transform(map: (T) -> R, backwardMap: (R) -> T): ObservableMutableProperty<R> =
  object : ObservableMutablePropertyTransformation<T, R>() {
    override val property = this@transform
    override fun map(value: T) = map(value)
    override fun backwardMap(value: R) = backwardMap(value)
  }

/**
 * Creates observable mutable property which ignores invalid values.
 * I.e. It doesn't store value in [this] property if [condition] returns false.
 */
@ApiStatus.Experimental
fun <T> ObservableMutableProperty<T>.backwardFilter(condition: (T) -> Boolean): ObservableMutableProperty<T> =
  object : ObservableMutablePropertyFiltration<T>() {
    override val property = this@backwardFilter
    override fun backwardFilter(value: T) = condition(value)
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

/**
 * Creates observable property that represents custom operation between several properties.
 * @param operation defines operation between properties' values.
 */
fun <T, R> operation(
  properties: List<ObservableProperty<T>>,
  operation: (List<T>) -> R
): ObservableProperty<R> =
  object : AbstractDelegateObservableProperty<R>(properties) {
    override fun get(): R = operation(properties.map { it.get() })
  }

/**
 * Creates observable property that represents custom operation between several properties.
 * @param operation defines operation between properties' values.
 */
fun <T, R> operation(
  vararg properties: ObservableProperty<T>,
  operation: (List<T>) -> R
): ObservableProperty<R> =
  operation(properties.toList(), operation)

private abstract class ObservableMutablePropertyTransformation<T, R> :
  ObservablePropertyTransformation<T, R>(),
  ObservableMutableProperty<R> {

  abstract override val property: ObservableMutableProperty<T>

  protected abstract fun backwardMap(value: R): T

  override fun set(value: R) =
    property.set(backwardMap(value))
}

private abstract class ObservablePropertyTransformation<T, R> : ObservableProperty<R> {

  protected abstract val property: ObservableProperty<T>

  protected abstract fun map(value: T): R

  override fun get(): R =
    map(property.get())

  override fun afterChange(parentDisposable: Disposable?, listener: (R) -> Unit) {
    property.afterChange(parentDisposable) { listener(map(it)) }
  }
}

private abstract class ObservableMutablePropertyFiltration<T> : ObservableMutableProperty<T> {

  protected abstract val property: ObservableMutableProperty<T>

  protected abstract fun backwardFilter(value: T): Boolean

  override fun get(): T {
    return property.get()
  }

  override fun set(value: T) {
    if (backwardFilter(value)) {
      property.set(value)
    }
  }

  override fun afterChange(parentDisposable: Disposable?, listener: (T) -> Unit) {
    property.afterChange(parentDisposable) { listener(it) }
  }
}

private abstract class AbstractDelegateObservableProperty<T>(
  private val properties: List<ObservableProperty<*>>
) : ObservableProperty<T> {

  constructor(vararg properties: ObservableProperty<*>) : this(properties.toList())

  override fun afterChange(parentDisposable: Disposable?, listener: (T) -> Unit) {
    for (property in properties) {
      property.afterChange(parentDisposable) { listener(get()) }
    }
  }
}
