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
 * @param isBlockPropagation if true then property changes propagation will be blocked through modified properties
 */
class PropertyGraph(debugName: String? = null, private val isBlockPropagation: Boolean = true) {
  @Deprecated("Please recompile code", level = DeprecationLevel.HIDDEN)
  @ApiStatus.ScheduledForRemoval(inVersion = "2022.1")
  constructor(debugName: String? = null) : this(debugName, true)

  private val propagation = AnonymousParallelOperationTrace((if (debugName == null) "" else " of $debugName") + ": Graph propagation")
  private val properties = ConcurrentHashMap<ObservableProperty<*>, PropertyNode>()
  private val dependencies = ConcurrentHashMap<PropertyNode, CopyOnWriteArrayList<Dependency<*>>>()
  private val recursionGuard = RecursionManager.createGuard<PropertyNode>(PropertyGraph::class.java.name)

  fun <T> dependsOn(child: AtomicProperty<T>, parent: ObservableProperty<*>, update: () -> T) {
    addDependency(child, parent) { updateAndGet { update() } }
  }

  private fun <T> addDependency(child: AtomicProperty<T>, parent: ObservableProperty<*>, update: AtomicProperty<T>.() -> Unit) {
    val childNode = properties[child] ?: throw IllegalArgumentException("Unregistered child property")
    val parentNode = properties[parent] ?: throw IllegalArgumentException("Unregistered parent property")
    dependencies.putIfAbsent(parentNode, CopyOnWriteArrayList())
    val children = dependencies.getValue(parentNode)
    children.add(Dependency(childNode, child, update))
  }

  fun afterPropagation(listener: () -> Unit) {
    propagation.afterOperation(listener)
  }

  fun register(property: ObservableProperty<*>) {
    val node = PropertyNode()
    properties[property] = node
    property.afterChange {
      recursionGuard.doPreventingRecursion(node, false) {
        propagation.task {
          node.isPropagationBlocked = isBlockPropagation
          propagateChange(node)
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

  @TestOnly
  fun isPropagationBlocked(property: ObservableProperty<*>) =
    properties.getValue(property).isPropagationBlocked

  private inner class PropertyNode {
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