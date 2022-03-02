// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.observable.properties

import com.intellij.openapi.Disposable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Suppress("DEPRECATION")
class GraphPropertyImpl<T>(private val propertyGraph: PropertyGraph, initial: () -> T)
  : GraphProperty<T>, AtomicLazyProperty<T>(initial) {

  override fun dependsOn(parent: ObservableProperty<*>, update: () -> T) {
    propertyGraph.dependsOn(this, parent, update)
  }

  override fun afterPropagation(listener: () -> Unit) {
    propertyGraph.afterPropagation(listener = listener)
  }

  override fun afterPropagation(parentDisposable: Disposable?, listener: () -> Unit) {
    propertyGraph.afterPropagation(parentDisposable, listener)
  }

  init {
    propertyGraph.register(this)
  }

  @Deprecated("Use set instead")
  @ApiStatus.ScheduledForRemoval
  override fun reset() =
    super<AtomicLazyProperty>.reset()

  @Deprecated("Use afterChange instead")
  @ApiStatus.ScheduledForRemoval
  override fun afterReset(listener: () -> Unit) =
    super<AtomicLazyProperty>.afterReset(listener)

  @Deprecated("Use afterChange instead")
  @ApiStatus.ScheduledForRemoval
  override fun afterReset(listener: () -> Unit, parentDisposable: Disposable) =
    super<AtomicLazyProperty>.afterReset(listener, parentDisposable)

  companion object {
    @Deprecated("Please use PropertyGraph.property instead", ReplaceWith("property(initial)"))
    @ApiStatus.ScheduledForRemoval
    fun <T> PropertyGraph.graphProperty(initial: T): GraphProperty<T> = property(initial)

    @Deprecated("Please use PropertyGraph.lazyProperty instead", ReplaceWith("lazyProperty(initial)"))
    @ApiStatus.ScheduledForRemoval
    fun <T> PropertyGraph.graphProperty(initial: () -> T): GraphProperty<T> = lazyProperty(initial)
  }
}