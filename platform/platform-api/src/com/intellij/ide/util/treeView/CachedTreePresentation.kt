// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.treeView

import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.util.treeView.TreeState.CachedPresentationDataImpl
import com.intellij.ui.treeStructure.CachingTreePath
import com.intellij.util.SlowOperations
import com.intellij.util.containers.nullize
import com.intellij.util.ui.tree.TreeUtil
import org.jetbrains.annotations.ApiStatus.Internal
import javax.swing.JTree
import javax.swing.tree.TreeModel
import javax.swing.tree.TreePath

@Internal
interface CachedTreePresentationSupport {
  @Internal
  fun setCachedPresentation(presentation: CachedTreePresentation?)
}

@Internal
interface TreeNodeWithCacheableAttributes {
  @Internal
  fun getCacheableAttributes(): Map<String, String>?
}

@Internal
class CachedTreePresentationData(
  val pathElement: CachedTreePathElement,
  val presentation: CachedPresentationData,
  val extraAttributes: Map<String, String>?,
  val children: List<CachedTreePresentationData>,
) {
  companion object {
    @JvmStatic fun createFromTree(tree: JTree): CachedTreePresentationData? {
      val model = tree.model
      if (model == null) return null
      return createPresentation(tree, model, null, model.root)
    }

    private fun createPresentation(
      tree: JTree,
      model: TreeModel,
      parentPath: TreePath?,
      node: Any?
    ): CachedTreePresentationData? {
      if (node == null) return null
      val userObject = TreeUtil.getUserObject(node)
      if (userObject is PresentableNodeDescriptor<*>) {
        val presentation = userObject.presentation
        val children = mutableListOf<CachedTreePresentationData>()
        val iconData = getIconData(presentation.getIcon(false))
        val extraAttrs = SlowOperations.knownIssue("IJPL-162819").use {
          (userObject as? TreeNodeWithCacheableAttributes)?.getCacheableAttributes()
        }
        val isLeaf = model.isLeaf(node)
        val result = CachedTreePresentationData(
          TreeState.PathElement(TreeState.calcId(userObject), TreeState.calcType(userObject), 0, null),
          CachedPresentationDataImpl(presentation.presentableText ?: "", iconData, isLeaf),
          extraAttrs,
          children
        )
        val nodePath = if (parentPath == null) CachingTreePath(node) else parentPath.pathByAddingChild(node)
        if (tree.isExpanded(nodePath)) {
          val childCount = model.getChildCount(node)
          for (i in 0 until childCount) {
            val child = model.getChild(node, i)
            val childPresentation = createPresentation(tree, model, nodePath, child) ?: continue
            children.add(childPresentation)
          }
        }
        return result
      }
      return null
    }
  }

  override fun toString(): String = "$pathElement $presentation"

  fun createTree(): CachedTreePresentation = CachedTreePresentation(this)

}

@Internal
interface CachedTreePathElement {
  val type: String?
  val id: String?
  fun matches(node: Any): Boolean
}

@Internal
interface CachedPresentationData {
  val text: String
  val iconData: CachedIconPresentation?
  val isLeaf: Boolean
}

@Internal
data class CachedIconPresentation(
  val path: String,
  val plugin: String,
  val module: String?,
)

@Internal
class CachedTreePresentationNode(
  val data: CachedTreePresentationData,
) : PresentableNodeDescriptor<CachedTreePresentationData>(null, null), PathElementIdProvider, TreeNodeWithCacheableAttributes {

  val isLeaf: Boolean
    get() = data.presentation.isLeaf

  var isExpanded = data.children.isNotEmpty()

  init {
    update() // It's cheap, so we don't want to wait for a BGT update here.
  }

  fun matches(node: Any): Boolean = data.pathElement.matches(node)

  override fun getPathElementType(): String = data.pathElement.type ?: ""

  override fun getPathElementId(): String = data.pathElement.id ?: ""

  override fun getElement(): CachedTreePresentationData = data

  override fun getCacheableAttributes(): Map<String, String>? = data.extraAttributes

  override fun update(presentation: PresentationData) {
    presentation.presentableText = data.presentation.text
    presentation.setIcon(data.presentation.icon)
  }

  override fun toString(): String = "(cached) ${super.toString()}"
}

@Internal
class CachedTreePresentation(rootPresentation: CachedTreePresentationData) {

  private val cachedRoot = CachedTreePresentationNode(rootPresentation)
  private val children = hashMapOf<CachedTreePresentationNode, List<CachedTreePresentationNode>>()
  private val cachedNodeByRealNode = hashMapOf<Any, CachedTreePresentationNode>()

  fun rootLoaded(realRoot: Any) {
    if (cachedRoot.matches(realRoot)) {
      cachedNodeByRealNode[realRoot] = cachedRoot
    }
  }

  fun childrenLoaded(parent: Any, children: List<Any>) {
    val cachedParent = getCachedNode(parent) ?: return
    val cachedChildren = getCachedChildren(cachedParent) ?: return
    if (cachedChildren.size != children.size) return
    for (index in children.indices) {
      val cached = cachedChildren[index]
      val real = children[index]
      if (cached.matches(real)) {
        cachedNodeByRealNode[real] = cached
      }
    }
  }

  fun getRoot(): Any = cachedRoot

  fun isLeaf(node: Any): Boolean = getCachedNode(node)?.isLeaf == true

  fun isExpanded(node: Any): Boolean = getCachedNode(node)?.isExpanded == true

  fun getChildren(parent: Any): List<Any>? = getCachedChildren(parent)?.nullize()

  private fun getCachedChildren(parent: Any): List<CachedTreePresentationNode>? {
    val cachedParent = getCachedNode(parent) ?: return null
    val cachedChildren = children[cachedParent]
    if (cachedChildren != null) return cachedChildren
    val nodeChildren = cachedParent.data.children.map { CachedTreePresentationNode(it) }
    children[cachedParent] = nodeChildren
    return nodeChildren
  }

  private fun getCachedNode(node: Any): CachedTreePresentationNode? {
    if (node is CachedTreePresentationNode) return node
    val userObject = TreeUtil.getUserObject(node)
    if (userObject is CachedTreePresentationNode) return userObject
    return cachedNodeByRealNode[node]
  }

  fun setExpanded(path: TreePath, isExpanded: Boolean) {
    val cachedNode = getCachedNode(path.lastPathComponent) ?: return
    cachedNode.isExpanded = isExpanded
  }

  fun getExpandedDescendants(model: TreeModel, parent: TreePath): Collection<TreePath> {
    val result = mutableListOf<TreePath>()
    getExpandedDescendants(model, parent, result)
    return result
  }

  private fun getExpandedDescendants(model: TreeModel, parentPath: TreePath, result: MutableList<TreePath>) {
    val parent = parentPath.lastPathComponent
    val cachedParent = getCachedNode(parent) ?: return
    if (!cachedParent.isExpanded) return
    result += parentPath
    val childCount = model.getChildCount(parent)
    for (i in 0 until childCount) {
      val child = model.getChild(parent, i)
      getExpandedDescendants(model, parentPath.pathByAddingChild(child), result)
    }
  }

}
