// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.treeView

import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.ui.treeStructure.CachingTreePath
import com.intellij.util.containers.nullize
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.resolvedPromise
import javax.swing.event.TreeModelListener
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeModel
import javax.swing.tree.TreePath

@ApiStatus.Experimental
class DefaultTreeModelWithCachedPresentation : TreeModel, CachedTreePresentationSupport {

  private val delegate = DefaultTreeModel(null)
  private var cachedPresentationApplier: CachedPresentationApplier? = null

  @get:ApiStatus.Internal
  @set:ApiStatus.Internal
  override var cachedPresentation: CachedTreePresentation? = null
    set(value) {
      LOG.debug("Applying the cached presentation")
      field = value
      if (value != null) {
        cachedPresentationApplier = CachedPresentationApplier(value)
      }
      cachedPresentationApplier?.checkDone() // the "nothing to apply" case
      if (LOG.isTraceEnabled) {
        dumpTree()
      }
    }

  fun promiseRealNodes(): Promise<List<TreePath>> {
    return cachedPresentationApplier?.promise ?: resolvedPromise(emptyList())
  }

  override fun getRoot(): DefaultMutableTreeNode? {
    return delegate.root as DefaultMutableTreeNode?
  }

  override fun getChild(parent: Any, index: Int): DefaultMutableTreeNode {
    return delegate.getChild(parent, index) as DefaultMutableTreeNode
  }

  override fun getChildCount(parent: Any): Int {
    return delegate.getChildCount(parent)
  }

  override fun isLeaf(node: Any): Boolean {
    return delegate.isLeaf(node)
  }

  override fun getIndexOfChild(parent: Any, child: Any): Int {
    return delegate.getIndexOfChild(parent, child)
  }

  override fun addTreeModelListener(l: TreeModelListener?) {
    delegate.addTreeModelListener(l)
  }

  override fun removeTreeModelListener(l: TreeModelListener?) {
    delegate.removeTreeModelListener(l)
  }

  override fun valueForPathChanged(path: TreePath, newValue: Any) {
    throw UnsupportedOperationException("Not supported unless overridden")
  }

  fun setRoot(newRoot: DefaultMutableTreeNode?) {
    LOG.debug { "Set root: $newRoot" }
    cachedPresentationApplier?.rootLoaded(newRoot)
    delegate.setRoot(newRoot)
    cachedPresentationApplier?.checkDone()
    if (LOG.isTraceEnabled && newRoot != null) {
      dumpTree()
    }
  }

  fun setChildren(parent: DefaultMutableTreeNode, children: List<DefaultMutableTreeNode>) {
    LOG.debug { "Set children of $parent: $children" }
    removeChildrenFromDelegate(parent)
    insertChildrenIntoDelegate(parent, children)
    cachedPresentationApplier?.checkDone()
    if (LOG.isTraceEnabled) {
      dumpTree()
    }
  }

  fun insertChild(parent: DefaultMutableTreeNode, index: Int, newChild: DefaultMutableTreeNode) {
    LOG.debug { "Insert at $index into $parent: $newChild" }
    delegate.insertNodeInto(newChild, parent, index)
    if (LOG.isTraceEnabled) {
      dumpTree()
    }
  }

  fun removeChild(parent: DefaultMutableTreeNode, index: Int) {
    LOG.debug { "Remove at $index from $parent" }
    delegate.removeNodeFromParent(getChild(parent, index))
    if (LOG.isTraceEnabled) {
      dumpTree()
    }
  }

  fun updateNode(node: DefaultMutableTreeNode, newValue: Any) {
    LOG.debug { "Update $node, new value: $newValue" }
    node.userObject = newValue
    delegate.nodeChanged(node)
  }

  private fun removeChildrenFromDelegate(parent: DefaultMutableTreeNode) {
    val removedChildCount = parent.childCount
    val removedChildren = mutableListOf<DefaultMutableTreeNode>()
    for (i in removedChildCount - 1 downTo 0) {
      removedChildren += parent.getChildAt(i) as DefaultMutableTreeNode
      parent.remove(i)
    }
    delegate.nodesWereRemoved(parent, IntArray(removedChildCount) { it }, removedChildren.toTypedArray())
    this.cachedPresentationApplier?.nodesRemoved(removedChildren)
  }

  private fun insertChildrenIntoDelegate(
    parent: DefaultMutableTreeNode,
    children: List<DefaultMutableTreeNode>,
  ) {
    this.cachedPresentationApplier?.childrenLoaded(CachingTreePath(parent.path), children)
    for ((index, child) in children.withIndex()) {
      parent.insert(child, index)
    }
    delegate.nodesWereInserted(parent, IntArray(children.size) { it })
  }

  private fun dumpTree() {
    val structure = buildString {
      dumpTree(root, level = 0)
    }
    LOG.trace { "The tree structure at the moment:\n$structure" }
  }

  private fun StringBuilder.dumpTree(node: DefaultMutableTreeNode?, level: Int) {
    if (node == null) return
    append(" ".repeat(level))
    append(node)
    append("\n")
    for (child in node.children()) {
      dumpTree(child as DefaultMutableTreeNode, level + 1)
    }
  }

  private inner class CachedPresentationApplier(
    val cachedPresentation: CachedTreePresentation
  ) {
    val promise = AsyncPromise<List<TreePath>>()
    private var cachedNodeCount = 0
    private val loadedRealNodes = mutableListOf<TreePath>()

    init {
      val realRoot = root
      if (realRoot == null) {
        val cachedRoot = DefaultMutableTreeNode(cachedPresentation.getRoot())
        applyCachedChildPresentations(cachedRoot)
        delegate.setRoot(cachedRoot)
        ++cachedNodeCount // count the root
      }
      else {
        cachedPresentation.rootLoaded(realRoot)
        applyCachedChildPresentations(realRoot)
        // Notify that the tree has changed only if it in fact DID change (any cached presentations were applied).
        if (cachedNodeCount > 0) {
          delegate.reload(realRoot)
        }
      }
    }

    private fun applyCachedChildPresentations(parent: DefaultMutableTreeNode) {
      val cachedChildren = cachedPresentation.getChildren(parent)?.nullize() ?: return
      val realChildCount = delegate.getChildCount(parent)
      if (realChildCount == 0) {
        for ((index, cachedChild) in cachedChildren.withIndex()) {
          val cachedChildNode = DefaultMutableTreeNode(cachedChild)
          applyCachedChildPresentations(cachedChildNode)
          parent.insert(cachedChildNode, index)
          ++cachedNodeCount
        }
      }
      else if (realChildCount == cachedChildren.size) {
        val realChildren = (0 until realChildCount).map { delegate.getChild(parent, it) as DefaultMutableTreeNode }
        cachedPresentation.childrenLoaded(parent, realChildren)
        for (realChild in realChildren) {
          applyCachedChildPresentations(realChild)
        }
      }
      // else mismatch, the cached info is outdated
    }

    fun rootLoaded(newRoot: DefaultMutableTreeNode?) {
      if (newRoot == null) return
      loadedRealNodes += CachingTreePath(newRoot)
      cachedPresentation.rootLoaded(newRoot)
      cachedNodeCount = 0 // we've just nuked the entire tree and about to recreate it
      applyCachedChildPresentations(newRoot)
    }

    fun childrenLoaded(parent: TreePath, children: List<DefaultMutableTreeNode>) {
      cachedPresentation.childrenLoaded(parent.lastPathComponent, children)
      for (child in children) {
        loadedRealNodes += parent.pathByAddingChild(child)
        applyCachedChildPresentations(child)
      }
    }

    fun nodesRemoved(removedNodes: List<DefaultMutableTreeNode>) {
      for (removedNode in removedNodes) {
        if (removedNode.userObject is CachedTreePresentationNode) --cachedNodeCount
        val children = (0 until removedNode.childCount).map { removedNode.getChildAt(it) as DefaultMutableTreeNode }
        nodesRemoved(children)
      }
    }

    fun checkDone() {
      if (cachedNodeCount <= 0) {
        if (cachedNodeCount < 0) {
          LOG.warn("There's a bug somewhere because the cached node count ended up negative: $cachedNodeCount")
        }
        done()
      }
    }

    private fun done() {
      LOG.debug { "Done with the cached presentation, loadedRealNodes.size=${loadedRealNodes.size}" }
      promise.setResult(loadedRealNodes)
      this@DefaultTreeModelWithCachedPresentation.cachedPresentationApplier = null
    }
  }
}

private val LOG = logger<DefaultTreeModelWithCachedPresentation>()
