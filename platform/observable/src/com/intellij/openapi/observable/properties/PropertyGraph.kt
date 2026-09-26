// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.observable.properties

import com.intellij.openapi.Disposable
import com.intellij.openapi.observable.operation.core.AtomicOperationTrace
import com.intellij.openapi.observable.operation.core.isOperationCompleted
import com.intellij.openapi.observable.operation.core.traceRun
import com.intellij.openapi.observable.operation.core.whenOperationFinished
import com.intellij.openapi.util.RecursionManager
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * PropertyGraph traces modifications inside observable properties. It creates a graph of dependent properties.
 *
 * PropertyGraph can recognize and stop infinite updates between properties.
 * For example, assume there are properties A -> B -> C -> A (-> means depends on) and property A is modified.
 * Then the PropertyGraph detects this cycle, and it doesn't make the last modification.
 *
 * PropertyGraph can block propagation through a property that was modified externally (outside the PropertyGraph).
 * This is needed for UI cases.
 * For example, assume there are two properties named id and name, and whenever id is modified, its value is copied to the name.
 * When name is now modified externally, the id is no longer copied to the name.
 *
 * @param isBlockPropagation if true then property changes propagation will be blocked through modified properties.
 */
class PropertyGraph(debugName: String? = null, private val isBlockPropagation: Boolean = true) {

  private val propagation = AtomicOperationTrace("Graph ${debugName ?: "UNKNOWN"} propagation")
  private val properties = ConcurrentHashMap<ObservableProperty<*>, Boolean>()
  private val dependencyMatrix = ConcurrentHashMap<ObservableProperty<*>, CopyOnWriteArrayList<Dependency<*>>>()
  private val recursionGuard = RecursionManager.createGuard<ObservableProperty<*>>(PropertyGraph::class.java.name)

  /**
   * Creates graph simple builder property.
   */
  fun <T> property(initial: T): GraphProperty<T> = GraphPropertyImpl(this) { initial }

  /**
   * Creates graph builder property with lazy initialization.
   */
  fun <T> lazyProperty(initial: () -> T): GraphProperty<T> = GraphPropertyImpl(this, initial)

  /**
   * Creates graph builder property which will be explicitly initialized.
   */
  fun <T> lateinitProperty(): GraphProperty<T> = lazyProperty { throw UninitializedPropertyAccessException() }

  /**
   * Creates dependency between [child] and [parent] properties.
   * @param deleteWhenChildModified if true then property changes propagation will be blocked when [child] is modified.
   * @param update, result of this function will be applied into [child] when [parent] is modified.
   * @see PropertyGraph
   */
  fun <T> dependsOn(
    child: ObservableMutableProperty<T>,
    parent: ObservableProperty<*>,
    deleteWhenChildModified: Boolean = isBlockPropagation,
    update: () -> T
  ) {
    registerIfNeeded(child)
    registerIfNeeded(parent)
    val dependencies = dependencyMatrix.computeIfAbsent(parent) { CopyOnWriteArrayList() }
    dependencies.add(Dependency(child, deleteWhenChildModified, update))
  }

  internal fun registerIfNeeded(property: ObservableProperty<*>) {
    if (properties.putIfAbsent(property, true) == null) {
      property.whenPropertyChanged {
        if (propagation.isOperationCompleted()) {
          removeDependenciesIfNeeded(property)
          recursionGuard.doPreventingRecursion(property, false) {
            propagateChange(property)
          }
        }
        else {
          propagateChange(property)
        }
      }
    }
  }

  private fun removeDependenciesIfNeeded(property: ObservableProperty<*>) {
    for (dependencies in dependencyMatrix.values) {
      dependencies.removeIf { it.property == property && it.deleteWhenPropertyModified }
    }
  }

  private fun propagateChange(property: ObservableProperty<*>) {
    propagation.traceRun {
      val dependencies = dependencyMatrix[property] ?: emptyList()
      for (dependency in dependencies) {
        recursionGuard.doPreventingRecursion(dependency.property, false) {
          dependency.applyUpdate()
        }
      }
    }
  }

  /**
   * Registers callback on propagation process.
   * @param listener is callback which will be called when properties changes are finished.
   * @see PropertyGraph
   */
  fun afterPropagation(listener: () -> Unit): Unit = afterPropagation(null, listener)

  /**
   * Registers callback on propagation process.
   * @param parentDisposable is used to arly unsubscribe from property graph propagation events.
   * @param listener is callback which will be called when properties changes are finished.
   * @see PropertyGraph
   */
  fun afterPropagation(parentDisposable: Disposable?, listener: () -> Unit) {
    propagation.whenOperationFinished(parentDisposable, listener)
  }

  private class Dependency<T>(
    val property: ObservableMutableProperty<T>,
    val deleteWhenPropertyModified: Boolean,
    private val update: () -> T
  ) {
    fun applyUpdate() {
      if (property is AtomicMutableProperty) {
        property.updateAndGet { update() }
      }
      else {
        property.set(update())
      }
    }
  }
}