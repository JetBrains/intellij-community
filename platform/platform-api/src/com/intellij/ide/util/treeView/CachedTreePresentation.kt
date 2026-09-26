// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.treeView

import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.util.treeView.TreeState.CachedPresentationDataImpl
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.treeStructure.CachingTreePath
import com.intellij.ui.treeStructure.TreeNodePresentationImpl
import com.intellij.ui.treeStructure.TreeNodeWithPresentation
import com.intellij.util.SlowOperations
import com.intellij.util.containers.nullize
import com.intellij.util.ui.tree.TreeUtil
import org.jetbrains.annotations.ApiStatus.Internal
import javax.swing.Icon
import javax.swing.JTree
import javax.swing.tree.TreeModel
import javax.swing.tree.TreePath

@Internal
interface CachedTreePresentationSupport {
  var cachedPresentation: CachedTreePresentation?

  fun applyAlreadyLoadedNodesTo(cachedPresentation: CachedTreePresentation) { }
}

@Internal
const val CACHED_TREE_PRESENTATION_PROPERTY: String = "CACHED_TREE_PRESENTATION"

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
      val presentation = getPresentationData(node) ?: getPresentationData(TreeUtil.getUserObject(node)) ?: return null
      val children = mutableListOf<CachedTreePresentationData>()
      val iconData = getIconData(presentation.icon)
      val isLeaf = model.isLeaf(node)
      val result = CachedTreePresentationData(
        TreeState.PathElement(TreeState.calcId(node), TreeState.calcType(node), 0, null),
        CachedPresentationDataImpl(presentation.text, iconData, isLeaf),
        presentation.cacheableAttributes,
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
  }

  override fun toString(): String = "$pathElement $presentation"

  fun createTree(): CachedTreePresentation = CachedTreePresentation(this)

}

private fun getPresentationData(nodeOrUserObject: Any?): NodePresentationData? {
  return when (nodeOrUserObject) {
    is PresentableNodeDescriptor<*> -> nodeOrUserObject.getPresentationData()
    is TreeNodeWithPresentation -> nodeOrUserObject.getPresentationData()
    else -> null
  }
}

private fun PresentableNodeDescriptor<*>.getPresentationData(): NodePresentationData {
  val presentation = presentation
  return NodePresentationData(
    presentation.getCacheableText(),
    presentation.getIcon(false),
    SlowOperations.knownIssue("IJPL-162819").use {
      (this as? TreeNodeWithCacheableAttributes)?.getCacheableAttributes()
    }
  )
}

private fun TreeNodeWithPresentation.getPresentationData(): NodePresentationData {
  val presentation = presentation as TreeNodePresentationImpl
  return NodePresentationData(
    presentation.getCacheableText(),
    presentation.icon,
    (this as? TreeNodeWithCacheableAttributes)?.getCacheableAttributes()
  )
}

private data class NodePresentationData(
  val text: String,
  val icon: Icon?,
  val cacheableAttributes: Map<String, String>?,
)

private fun PresentationData.getCacheableText(): String {
  var result = presentableText
  if (result?.isNotEmpty() == true) return result
  result = buildString {
    for (fragment in coloredText) {
      // Heuristics like in NodeRenderer: grayed stuff means secondary stuff.
      if (fragment.attributes.fgColor == SimpleTextAttributes.GRAYED_ATTRIBUTES.fgColor) break
      append(fragment.text)
    }
  }
  return result
}

private fun TreeNodePresentationImpl.getCacheableText(): String = mainText

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

  var isExpanded: Boolean = data.children.isNotEmpty()

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

    // here we match two arrays of possibly different sizes allowing extra items on one (any) side
    var realIndex = 0
    var cachedIndex = 0
    while (realIndex < children.size && cachedIndex < cachedChildren.size) {
      val cached = cachedChildren[cachedIndex]
      val real = children[realIndex]
      if (cached.matches(real)) {
        cachedNodeByRealNode[real] = cached
        realIndex++
        cachedIndex++
      }
      else {
        val realRemaining = children.size - realIndex
        val cachedRemaining = cachedChildren.size - cachedIndex
        when  {
          realRemaining > cachedRemaining -> realIndex++
          realRemaining < cachedRemaining -> cachedIndex++
          else -> {
            realIndex++
            cachedIndex++
          }
        }
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
