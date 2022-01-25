// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.observable.properties

import com.intellij.openapi.Disposable

@Deprecated("Use transformations from PropertyTransformationUtil",
            ReplaceWith("transform(transform, { it })", "com.intellij.openapi.observable.util.transform"))
fun <T> GraphProperty<T>.map(transform: (T) -> T) = transform(transform, { it })

@Deprecated("Use transformations from PropertyTransformationUtil",
            ReplaceWith("transform({ it }, transform)", "com.intellij.openapi.observable.util.transform"))
fun <T> GraphProperty<T>.comap(transform: (T) -> T) = transform({ it }, transform)

@Deprecated("Use transformations from PropertyTransformationUtil",
            ReplaceWith("transform(map, comap)", "com.intellij.openapi.observable.util.transform"))
fun <S, T> GraphProperty<S>.transform(map: (S) -> T, comap: (T) -> S): GraphProperty<T> =
  GraphPropertyView(this, map, comap)

@Deprecated("Use transformations from PropertyTransformationUtil",
            ReplaceWith("transform(transform, { it })", "com.intellij.openapi.observable.util.transform"))
fun <T> ObservableMutableProperty<T>.map(transform: (T) -> T) = transform(transform, { it })

@Deprecated("Use transformations from PropertyTransformationUtil",
            ReplaceWith("transform({ it }, transform)", "com.intellij.openapi.observable.util.transform"))
fun <T> ObservableMutableProperty<T>.comap(transform: (T) -> T) = transform({ it }, transform)

@Deprecated("Use transformations from PropertyTransformationUtil",
            ReplaceWith("transform(map, comap)", "com.intellij.openapi.observable.util.transform"))
fun <S, T> ObservableMutableProperty<S>.transform(map: (S) -> T, comap: (T) -> S): ObservableMutableProperty<T> =
  ObservableMutablePropertyView(this, map, comap)

@Suppress("DEPRECATION")
private class GraphPropertyView<S, T>(
  private val instance: GraphProperty<S>,
  map: (S) -> T,
  private val comap: (T) -> S
) : GraphProperty<T>, ObservableClearablePropertyView<S, T>(instance, map, comap) {
  override fun dependsOn(parent: ObservableProperty<*>, update: () -> T) =
    instance.dependsOn(parent) { comap(update()) }

  override fun afterPropagation(listener: () -> Unit) =
    instance.afterPropagation(listener)
}

@Suppress("DEPRECATION")
private open class ObservableClearablePropertyView<S, T>(
  private val instance: ObservableClearableProperty<S>,
  map: (S) -> T,
  comap: (T) -> S
) : ObservableClearableProperty<T>, ObservableMutablePropertyView<S, T>(instance, map, comap) {
  override fun reset() =
    instance.reset()

  override fun afterReset(listener: () -> Unit) =
    instance.afterReset(listener)

  override fun afterReset(listener: () -> Unit, parentDisposable: Disposable) =
    instance.afterReset(listener, parentDisposable)
}

private open class ObservableMutablePropertyView<S, T>(
  private val instance: ObservableMutableProperty<S>,
  private val map: (S) -> T,
  private val comap: (T) -> S
) : ObservableMutableProperty<T> {
  override fun get(): T =
    map(instance.get())

  override fun set(value: T) =
    instance.set(comap(value))

  override fun afterChange(listener: (T) -> Unit) =
    instance.afterChange { listener(map(it)) }

  override fun afterChange(listener: (T) -> Unit, parentDisposable: Disposable) =
    instance.afterChange({ listener(map(it)) }, parentDisposable)

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    return instance == other
  }

  override fun hashCode(): Int {
    return instance.hashCode()
  }
}