// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.observable.properties

class GraphPropertyImpl<T>(private val propertyGraph: PropertyGraph, initial: () -> T)
  : GraphProperty<T>, AtomicLazyProperty<T>(initial) {

  @Suppress("DEPRECATION")
  override fun dependsOn(parent: ObservableClearableProperty<*>) {
    propertyGraph.dependsOn(this, parent) { reset(); get() }
  }

  @Suppress("DEPRECATION")
  override fun dependsOn(parent: ObservableClearableProperty<*>, default: () -> T) {
    propertyGraph.dependsOn(this, parent, default)
  }

  override fun afterPropagation(listener: () -> Unit) {
    propertyGraph.afterPropagation(listener)
  }

  init {
    propertyGraph.register(this)
  }

  companion object {
    fun <T> PropertyGraph.graphProperty(initial: T) = graphProperty { initial }

    fun <T> PropertyGraph.graphProperty(initial: () -> T): GraphProperty<T> = GraphPropertyImpl(this, initial)

    fun <S, T> PropertyGraph.graphPropertyView(initial: () -> T, map: (S) -> T, comap: (T) -> S): GraphProperty<T> =
      this.graphProperty { comap(initial()) }.transform(map, comap)
  }
}