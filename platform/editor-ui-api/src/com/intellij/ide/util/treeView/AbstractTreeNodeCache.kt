// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util.treeView

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class AbstractTreeNodeCache<K, V : AbstractTreeNode<*>>(private val parent: AbstractTreeNode<*>, private val producer: (K) -> V?) {
  private val nodes = mutableMapOf<K, V?>()

  fun getNodes(keys: Collection<K>): List<V> {
    nodes.values.forEach { it?.parent = null } // mark all cached nodes as invalid
    val children = keys.mapNotNull { nodes.computeIfAbsent(it, producer) }
    children.forEach { it.parent = parent } // mark all computed nodes as valid
    nodes.values.removeIf { it?.parent == null } // remove invalid nodes
    return children
  }
}
