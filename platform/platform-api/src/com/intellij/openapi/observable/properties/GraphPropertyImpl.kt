// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.observable.properties

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Suppress("DEPRECATION")
class GraphPropertyImpl<T>(private val propertyGraph: PropertyGraph, initial: () -> T)
  : GraphProperty<T>, AtomicLazyProperty<T>(initial) {

  override fun dependsOn(parent: ObservableProperty<*>, update: () -> T) {
    propertyGraph.dependsOn(this, parent, update)
  }

  override fun afterPropagation(listener: () -> Unit) {
    propertyGraph.afterPropagation(listener)
  }

  init {
    propertyGraph.register(this)
  }

  companion object {
    @Deprecated("Please use PropertyGraph.property instead", ReplaceWith("property(initial)"))
    fun <T> PropertyGraph.graphProperty(initial: T): GraphProperty<T> = property(initial)

    @Deprecated("Please use PropertyGraph.lazyProperty instead", ReplaceWith("lazyProperty(initial)"))
    fun <T> PropertyGraph.graphProperty(initial: () -> T): GraphProperty<T> = lazyProperty(initial)
  }
}