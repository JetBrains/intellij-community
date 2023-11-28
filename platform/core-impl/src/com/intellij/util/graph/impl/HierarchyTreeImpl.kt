/*
 * Copyright 2000-2023 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util.graph.impl

import com.intellij.util.graph.HierarchyTree

class HierarchyTreeImpl<N : Any, GN : N> : HierarchyTree<N, GN> {

  private val hierarchyNodes = mutableSetOf<N>()
  private val nodes2ParentGroupsMap = mutableMapOf<N, GN>()
  private val groupNodes2Children = mutableMapOf<GN, MutableSet<N>>()

  override fun getAllHierarchyNodes(): Set<N> = hierarchyNodes

  override fun getParent(node: N): GN? {
    return nodes2ParentGroupsMap[node]
  }

  override fun getChildren(group: GN, isRecursively: Boolean): Set<N> {
    val children = mutableSetOf<N>()
    val ns = groupNodes2Children[group]
    ns?.let { children.addAll(it) }
    if (isRecursively) {
      ns?.forEach { child ->
        if (groupNodes2Children.containsKey(child)) {
          children.addAll(getChildren(child as GN, true))
        }
      }
    }
    return children
  }

  override fun connect(child: N, parent: GN) {
    hierarchyNodes.add(child)
    hierarchyNodes.add(parent)

    nodes2ParentGroupsMap[child] = parent
    groupNodes2Children.getOrPut(parent) { mutableSetOf() }.add(child)
  }

  override fun connect(children: Collection<N>, parent: GN) {
    hierarchyNodes.addAll(children)
    hierarchyNodes.add(parent)

    children.forEach { child ->
      nodes2ParentGroupsMap[child] = parent
    }
    groupNodes2Children.getOrPut(parent) { mutableSetOf() }.addAll(children)
  }

  override fun ungroupNodes(group: GN) {
    groupNodes2Children[group]?.forEach { child ->
      nodes2ParentGroupsMap.remove(child)
    }
    groupNodes2Children.remove(group)
    hierarchyNodes.remove(group)
  }

  override fun ungroupNodes(nodes: Collection<N>) {
    nodes.forEach { node ->
      groupNodes2Children[nodes2ParentGroupsMap[node]]?.remove(node)
      nodes2ParentGroupsMap.remove(node)
      if (!isNodeWithHierarchy(node)) {
        hierarchyNodes.remove(node)
      }
    }
  }

  override fun ungroupAllNodes() {
    groupNodes2Children.clear()
    nodes2ParentGroupsMap.clear()
    hierarchyNodes.clear()
  }

  @Suppress("UNCHECKED_CAST")
  override fun removeGroupWithItsNodes(group: GN) {
    groupNodes2Children[group]?.forEach { child ->
      if (groupNodes2Children.containsKey(child)) {
        removeGroupWithItsNodes(child as GN)
      }
      nodes2ParentGroupsMap.remove(child)
    }
    groupNodes2Children.remove(group)
    hierarchyNodes.remove(group)
  }

  private fun isNodeWithHierarchy(node: N): Boolean =
    nodes2ParentGroupsMap.containsKey(node) || groupNodes2Children.containsKey(node)
}