// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.observable.properties

import com.intellij.openapi.observable.operations.AnonymousParallelOperationTrace
import com.intellij.openapi.observable.operations.AnonymousParallelOperationTrace.Companion.task
import com.intellij.openapi.util.RecursionManager
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * PropertyGraph is traces modifications inside observable properties. It creates graph of dependent properties.
 *
 * PropertyGraph can recognize and stop infinity updates between properties. For example, we have properties
 * A -> B -> C -> A (-> depends on) and property A is modified. Then property graph detects this cycle, and it doesn't
 * make last modification.
 *
 * PropertyGraph can block propagation through property, which was modified externally (outside PropertyGraph).
 * It is needed for UI cases. For example, we have dependent properties id and name. When we modify property id, we put
 * id's value into property name. But if we modify property name and after that try to modify id property then property
 * name isn't modified automatically.
 *
 * @param isBlockPropagation if true then property changes propagation will be blocked through modified properties.
 */
class PropertyGraph(debugName: String? = null, private val isBlockPropagation: Boolean = true) {

  private val propagation = AnonymousParallelOperationTrace((if (debugName == null) "" else " of $debugName") + ": Graph propagation")
  private val properties = ConcurrentHashMap<ObservableProperty<*>, PropertyNode>()
  private val dependencies = ConcurrentHashMap<PropertyNode, CopyOnWriteArrayList<Dependency<*>>>()
  private val recursionGuard = RecursionManager.createGuard<PropertyNode>(PropertyGraph::class.java.name)

  /**
   * Creates graph simple builder property.
   */
  fun <T> property(initial: T): GraphProperty<T> = GraphPropertyImpl(this) { initial }

  /**
   * Creates graph builder property with lazy initialization.
   */
  fun <T> lazyProperty(initial: () -> T): GraphProperty<T> = GraphPropertyImpl(this, initial)

  /**
   * Creates dependency between [child] and [parent] properties.
   * @param update, result of this function will be applied into [child] when [parent] is modified.
   * @see PropertyGraph
   */
  fun <T> dependsOn(child: AtomicProperty<T>, parent: ObservableProperty<*>, update: () -> T) {
    addDependency(child, parent) { updateAndGet { update() } }
  }

  /**
   * Registers callback on propagation process.
   * @see PropertyGraph
   */
  fun afterPropagation(listener: () -> Unit) {
    propagation.afterOperation(listener)
  }

  private fun <T> addDependency(
    child: AtomicProperty<T>,
    parent: ObservableProperty<*>,
    update: AtomicProperty<T>.() -> Unit
  ) {
    val childNode = getOrRegisterNode(child)
    val parentNode = getOrRegisterNode(parent)
    dependencies.computeIfAbsent(parentNode) { CopyOnWriteArrayList() }
      .add(Dependency(childNode, child, update))
  }

  private fun getOrRegisterNode(property: ObservableProperty<*>): PropertyNode {
    return properties.computeIfAbsent(property) {
      PropertyNode().also { node ->
        property.afterChange {
          recursionGuard.doPreventingRecursion(node, false) {
            propagation.task {
              node.isPropagationBlocked = isBlockPropagation
              propagateChange(node)
            }
          }
        }
      }
    }
  }

  private fun propagateChange(parent: PropertyNode) {
    val dependencies = dependencies[parent] ?: return
    for (dependency in dependencies) {
      val child = dependency.node
      if (child.isPropagationBlocked) continue
      recursionGuard.doPreventingRecursion(child, false) {
        dependency.applyUpdate()
        propagateChange(child)
      }
    }
  }

  @ApiStatus.Internal
  fun register(property: ObservableProperty<*>) {
    getOrRegisterNode(property)
  }

  @TestOnly
  fun isPropagationBlocked(property: ObservableProperty<*>) =
    properties.getValue(property).isPropagationBlocked

  private class PropertyNode {
    @Volatile
    var isPropagationBlocked = false
  }

  private data class Dependency<T>(
    val node: PropertyNode,
    val property: AtomicProperty<T>,
    val update: AtomicProperty<T>.() -> Unit
  ) {
    fun applyUpdate() = property.update()
  }
}