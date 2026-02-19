// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.observable.properties

import com.intellij.openapi.Disposable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Suppress("DEPRECATION")
class GraphPropertyImpl<T>(private val propertyGraph: PropertyGraph, initial: () -> T)
  : GraphProperty<T>, AtomicLazyProperty<T>(initial) {

  init {
    propertyGraph.registerIfNeeded(this)
  }

  override fun dependsOn(parent: ObservableProperty<*>, update: () -> T) {
    propertyGraph.dependsOn(this, parent, update = update)
  }

  override fun dependsOn(parent: ObservableProperty<*>, deleteWhenModified: Boolean, update: () -> T) {
    propertyGraph.dependsOn(this, parent, deleteWhenModified, update = update)
  }

  override fun afterPropagation(listener: () -> Unit) {
    propertyGraph.afterPropagation(listener = listener)
  }

  override fun afterPropagation(parentDisposable: Disposable?, listener: () -> Unit) {
    propertyGraph.afterPropagation(parentDisposable, listener)
  }

  @Deprecated("Use set instead")
  @ApiStatus.ScheduledForRemoval
  override fun reset(): Unit =
    super<AtomicLazyProperty>.reset()

  @Deprecated("Use afterChange instead")
  @ApiStatus.ScheduledForRemoval
  override fun afterReset(listener: () -> Unit): Unit =
    super<AtomicLazyProperty>.afterReset(listener)

  @Deprecated("Use afterChange instead")
  @ApiStatus.ScheduledForRemoval
  override fun afterReset(listener: () -> Unit, parentDisposable: Disposable): Unit =
    super<AtomicLazyProperty>.afterReset(listener, parentDisposable)
}