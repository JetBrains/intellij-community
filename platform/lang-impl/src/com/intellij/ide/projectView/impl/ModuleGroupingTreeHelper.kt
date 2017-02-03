/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.ide.projectView.impl

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleGrouper
import com.intellij.openapi.util.Pair
import com.intellij.util.ui.tree.TreeUtil
import org.jetbrains.annotations.TestOnly
import java.util.*
import javax.swing.JTree
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.MutableTreeNode
import javax.swing.tree.TreeNode

/**
 * Provides methods to build trees where nodes are grouped by modules (and optionally by module groups). 
 *
 * @author nik
 */
class ModuleGroupingTreeHelper<N: MutableTreeNode> private constructor(
  private val groupingEnabled: Boolean,
  private val grouper: ModuleGrouper,
  private val moduleGroupNodeFactory: (ModuleGroup) -> N,
  private val moduleNodeFactory: (Module, ModuleGrouper) -> N,
  private val nodeComparator: Comparator<in N>
) {
  private val nodeForGroup = HashMap<ModuleGroup, N>()
  private val nodeData = HashMap<N, ModuleTreeNodeData>()

  companion object {
    @JvmStatic
    fun <N : MutableTreeNode> forEmptyTree(groupingEnabled: Boolean, grouper: ModuleGrouper,
                                           moduleGroupNodeFactory: (ModuleGroup) -> N, moduleNodeFactory: (Module, ModuleGrouper) -> N,
                                           nodeComparator: Comparator<in N>) =
      ModuleGroupingTreeHelper(groupingEnabled, grouper, moduleGroupNodeFactory, moduleNodeFactory, nodeComparator)

    @JvmStatic
    fun <N : MutableTreeNode> forTree(rootNode: N, moduleGroupByNode: (N) -> ModuleGroup?, moduleByNode: (N) -> Module?,
                                      groupingEnabled: Boolean, grouper: ModuleGrouper,
                                      moduleGroupNodeFactory: (ModuleGroup) -> N, moduleNodeFactory: (Module, ModuleGrouper) -> N,
                                      nodeComparator: Comparator<in N>): ModuleGroupingTreeHelper<N> {
      val helper = ModuleGroupingTreeHelper(groupingEnabled, grouper, moduleGroupNodeFactory, moduleNodeFactory, nodeComparator)
      TreeUtil.traverse(rootNode) { node ->
        @Suppress("UNCHECKED_CAST")
        val group = moduleGroupByNode(node as N)
        val module = moduleByNode(node)
        if (group != null) {
          helper.nodeForGroup[group] = node
        }
        if (group != null || module != null) {
          helper.nodeData[node] = ModuleTreeNodeData(module, group)
        }
        true
      }
      return helper
    }
  }

  fun createModuleNodes(modules: Collection<Module>, rootNode: N, model: DefaultTreeModel): List<N> {
    val nodes = modules.map { createModuleNode(it, rootNode, model) }
    TreeUtil.sortRecursively(rootNode, nodeComparator)
    model.nodeStructureChanged(rootNode)
    return nodes
  }

  private fun createModuleNode(module: Module, rootNode: N, model: DefaultTreeModel): N {
    val group = ModuleGroup(grouper.getGroupPath(module))
    val parentNode = getOrCreateNodeForModuleGroup(group, rootNode, model, true)
    val moduleNode = moduleNodeFactory(module, grouper)
    insertModuleNode(moduleNode, parentNode, module, model, true)
    return moduleNode
  }

  /**
   * If [bulkOperation] is true, no events will be fired and new node will be added into arbitrary place in the children list
   */
  private fun insertModuleNode(moduleNode: N, parentNode: N, module: Module, model: DefaultTreeModel, bulkOperation: Boolean) {
    val moduleAsGroup = moduleAsGroup(module)
    if (moduleAsGroup != null) {
      val oldModuleGroupNode = nodeForGroup[moduleAsGroup]
      if (oldModuleGroupNode != null) {
        moveChildren(oldModuleGroupNode, moduleNode, model)
        model.removeNodeFromParent(oldModuleGroupNode)
        removeNode(oldModuleGroupNode)
      }
      nodeForGroup[moduleAsGroup] = moduleNode
      nodeData[moduleNode] = ModuleTreeNodeData(module, moduleAsGroup)
    }
    else {
      nodeData[moduleNode] = ModuleTreeNodeData(module, null)
    }

    insertNode(moduleNode, parentNode, model, bulkOperation)
  }

  private fun moduleAsGroup(module: Module) = grouper.getModuleAsGroupPath(module)?.let(::ModuleGroup)

  private fun moveChildren(fromNode: N, toNode: N, model: DefaultTreeModel) {
    val children = TreeUtil.listChildren(fromNode)
    moveChildren(children, toNode, model)
  }

  private fun moveChildren(children: List<TreeNode>, toNode: N, model: DefaultTreeModel) {
    TreeUtil.addChildrenTo(toNode, children)
    TreeUtil.sortChildren(toNode, nodeComparator)
    model.nodeStructureChanged(toNode)
  }

  /**
   * If [bulkOperation] is true, no events will be fired and new node will be added into arbitrary place in the children list
   */
  private fun getOrCreateNodeForModuleGroup(group: ModuleGroup, rootNode: N, model: DefaultTreeModel, bulkOperation: Boolean): N {
    if (!groupingEnabled) return rootNode

    var parentNode = rootNode
    val path = group.groupPathList
    for (i in path.indices) {
      val current = ModuleGroup(path.subList(0, i+1))
      var node = nodeForGroup[current]
      if (node == null) {
        node = moduleGroupNodeFactory(current)
        insertNode(node, parentNode, model, bulkOperation)
        nodeForGroup[current] = node
        nodeData[node] = ModuleTreeNodeData(null,group)
      }
      parentNode = node
    }
    return parentNode
  }

  private fun insertNode(node: N, parentNode: N, model: DefaultTreeModel, bulkOperation: Boolean) {
    if (bulkOperation) {
      parentNode.insert(node, parentNode.childCount)
    }
    else {
      TreeUtil.insertNode(node, parentNode, model, nodeComparator)
    }
  }

  fun moveAllModuleNodesToProperGroups(rootNode: N, model: DefaultTreeModel) {
    val modules = nodeData.values.map { it.module }.filterNotNull()
    nodeData.keys.forEach { it.removeFromParent() }
    nodeData.clear()
    nodeForGroup.clear()
    createModuleNodes(modules, rootNode, model)
  }

  fun moveModuleNodesToProperGroup(nodes: List<Pair<N, Module>>, rootNode: N, model: DefaultTreeModel, tree: JTree) {
    nodes.forEach {
      moveModuleNodeToProperGroup(it.first, it.second, rootNode, model, tree)
    }
  }

  fun moveModuleNodeToProperGroup(node: N, module: Module, rootNode: N, model: DefaultTreeModel, tree: JTree): N {
    val actualGroup = ModuleGroup(grouper.getGroupPath(module))
    val parent = node.parent
    val nodeAsGroup = nodeData[node]?.group
    val expectedParent = if (groupingEnabled && !actualGroup.groupPathList.isEmpty()) nodeForGroup[actualGroup] else rootNode
    if (expectedParent == parent && nodeAsGroup == moduleAsGroup(module)) {
      return node
    }

    val selectionPath = tree.selectionPath
    val wasSelected = selectionPath?.lastPathComponent == node
    model.removeNodeFromParent(node)

    removeNode(node)
    if (nodeAsGroup != null) {
      val childrenToKeep = TreeUtil.listChildren(node).filter { it in nodeData }
      if (childrenToKeep.isNotEmpty()) {
        val newGroupNode = getOrCreateNodeForModuleGroup(nodeAsGroup, rootNode, model, false)
        moveChildren(childrenToKeep, newGroupNode, model)
      }
    }

    removeEmptySyntheticModuleGroupNodes(parent, model)

    val newParent = getOrCreateNodeForModuleGroup(actualGroup, rootNode, model, false)
    val newNode = moduleNodeFactory(module, grouper)
    insertModuleNode(newNode, newParent, module, model, false)

    if (wasSelected) {
      tree.expandPath(TreeUtil.getPath(rootNode, newParent))
      tree.selectionPath = TreeUtil.getPath(rootNode, newNode)
    }
    return newNode
  }

  private fun removeEmptySyntheticModuleGroupNodes(parentNode: TreeNode?, model: DefaultTreeModel) {
    var parent = parentNode
    while (parent is MutableTreeNode && parent in nodeData && nodeData[parent]?.module == null && parent.childCount == 0) {
      val grandParent = parent.parent
      model.removeNodeFromParent(parent)
      removeNode(parent as N)
      parent = grandParent
    }
  }

  private fun removeNode(node: N) {
    val group = nodeData.remove(node)?.group
    if (group != null) {
      nodeForGroup.remove(group)
    }
  }

  @TestOnly
  fun getNodeForGroupMap() = Collections.unmodifiableMap(nodeForGroup)

  @TestOnly
  fun getModuleByNodeMap() = nodeData.mapValues { it.value.module }.filterValues { it != null }

  @TestOnly
  fun getGroupByNodeMap() = nodeData.mapValues { it.value.group }.filterValues { it != null }
}

private class ModuleTreeNodeData(val module: Module?, val group: ModuleGroup?)