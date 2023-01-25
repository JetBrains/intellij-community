// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.observable.properties

import com.intellij.openapi.Disposable
import org.jetbrains.annotations.ApiStatus

@Deprecated("Use transformations from PropertyOperationUtil",
            ReplaceWith("transform(transform, { it })", "com.intellij.openapi.observable.util.transform"))
@ApiStatus.ScheduledForRemoval
fun <T> GraphProperty<T>.map(transform: (T) -> T) = transform(transform, { it })

@Deprecated("Use transformations from PropertyOperationUtil",
            ReplaceWith("transform(map, comap)", "com.intellij.openapi.observable.util.transform"))
@ApiStatus.ScheduledForRemoval
fun <S, T> GraphProperty<S>.transform(map: (S) -> T, comap: (T) -> S): GraphProperty<T> =
  GraphPropertyView(this, map, comap)

@Deprecated("Use transformations from PropertyOperationUtil",
            ReplaceWith("transform(transform, { it })", "com.intellij.openapi.observable.util.transform"))
@ApiStatus.ScheduledForRemoval
fun <T> ObservableMutableProperty<T>.map(transform: (T) -> T) = transform(transform, { it })

@Deprecated("Use transformations from PropertyOperationUtil",
            ReplaceWith("transform(map, comap)", "com.intellij.openapi.observable.util.transform"))
@ApiStatus.ScheduledForRemoval
fun <S, T> ObservableMutableProperty<S>.transform(map: (S) -> T, comap: (T) -> S): ObservableMutableProperty<T> =
  ObservableMutablePropertyView(this, map, comap)

@Suppress("DEPRECATION")
private class GraphPropertyView<S, T>(
  private val instance: GraphProperty<S>,
  map: (S) -> T,
  private val comap: (T) -> S
) : GraphProperty<T>, ObservableMutablePropertyView<S, T>(instance, map, comap) {

  override fun dependsOn(parent: ObservableProperty<*>, update: () -> T) =
    instance.dependsOn(parent) { comap(update()) }

  override fun dependsOn(parent: ObservableProperty<*>, deleteWhenModified: Boolean, update: () -> T) =
    instance.dependsOn(parent, deleteWhenModified) { comap(update()) }

  override fun afterPropagation(listener: () -> Unit) =
    instance.afterPropagation(listener)

  override fun afterPropagation(parentDisposable: Disposable?, listener: () -> Unit) =
    instance.afterPropagation(parentDisposable, listener)

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

  override fun afterChange(parentDisposable: Disposable?, listener: (T) -> Unit) =
    instance.afterChange(parentDisposable) { listener(map(it)) }
}