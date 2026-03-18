// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.treeView

import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.ui.treeStructure.CachingTreePath
import com.intellij.util.containers.nullize
import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenHashMap
import it.unimi.dsi.fastutil.objects.Object2IntMap
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

  @ApiStatus.Internal
  override fun applyAlreadyLoadedNodesTo(cachedPresentation: CachedTreePresentation) {
    LOG.debug("Applying the already loaded nodes to the cached presentation before using it")
    val root = this.root ?: return
    if (root.isCached) return
    LOG.trace { "Marking the root as loaded: $root" }
    cachedPresentation.rootLoaded(root)
    applyAlreadyLoadedChildren(cachedPresentation, root)
  }

  private fun applyAlreadyLoadedChildren(cachedPresentation: CachedTreePresentation, parent: DefaultMutableTreeNode) {
    val children = parent.children().toList().filterIsInstance<DefaultMutableTreeNode>()
    if (children.any { it.isCached }) return // children not loaded yet, `any` or `all` doesn't matter, they're removed all at once
    // Important: the model can be huge, the cached presentation usually isn't.
    // Therefore, we must not traverse the entire model to save time.
    // We only inform the presentation of the real children that correspond to some cached nodes.
    if (cachedPresentation.getChildren(parent) == null) return
    LOG.trace { "Marking the children as loaded, the parent is $parent, the children is $children" }
    cachedPresentation.childrenLoaded(parent, children)
    for (child in children) {
      applyAlreadyLoadedChildren(cachedPresentation, child)
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

  fun updateChildren(
    parent: DefaultMutableTreeNode,
    newChildren: List<DefaultMutableTreeNode>,
    findOldByNew: (DefaultMutableTreeNode) -> DefaultMutableTreeNode?,
  ) {
    val hadChildren = parent.childCount > 0
    val willHaveChildren = newChildren.isNotEmpty()
    if (!hadChildren && !willHaveChildren) return
    if (!hadChildren || !willHaveChildren) {
      setChildren(parent, newChildren)
      return
    }

    val oldChildren = parent.children().asSequence().mapTo(mutableListOf()) { it as DefaultMutableTreeNode }

    val removedWithIndices = computeRemoved(oldChildren, newChildren, findOldByNew)
    removeChildrenFromDelegate(parent, removedWithIndices)

    val addedWithIndices = computeAdded(newChildren, findOldByNew)
    insertChildrenIntoDelegate(parent, addedWithIndices)
    
    val updatedWithIndices = computeUpdated(newChildren, findOldByNew)
    updateChildrenInDelegate(parent, updatedWithIndices)

    cachedPresentationApplier?.checkDone()
    if (LOG.isTraceEnabled) {
      dumpTree()
    }
  }

  private fun computeRemoved(
    oldChildren: MutableList<DefaultMutableTreeNode>,
    newChildren: List<DefaultMutableTreeNode>,
    findOldByNew: (DefaultMutableTreeNode) -> DefaultMutableTreeNode?,
  ): Object2IntMap<DefaultMutableTreeNode> {
    val removedWithIndices = Object2IntLinkedOpenHashMap<DefaultMutableTreeNode>()
    for ((i, oldChild) in oldChildren.withIndex()) {
      removedWithIndices[oldChild] = i
    }
    for (newChild in newChildren) {
      val oldChild = findOldByNew(newChild)
      if (oldChild != null) { // changed, not removed
        removedWithIndices.removeInt(oldChild)
      }
    }
    return removedWithIndices
  }

  private fun computeAdded(
    newChildren: List<DefaultMutableTreeNode>,
    findOldByNew: (DefaultMutableTreeNode) -> DefaultMutableTreeNode?,
  ): Object2IntMap<DefaultMutableTreeNode> {
    val addedWithIndices = Object2IntLinkedOpenHashMap<DefaultMutableTreeNode>()
    for ((i, newChild) in newChildren.withIndex()) {
      if (findOldByNew(newChild) == null) {
        addedWithIndices[newChild] = i
      }
    }
    return addedWithIndices
  }

  private fun computeUpdated(
    newChildren: List<DefaultMutableTreeNode>,
    findOldByNew: (DefaultMutableTreeNode) -> DefaultMutableTreeNode?,
  ): Object2IntMap<DefaultMutableTreeNode> {
    val updatedWithIndices = Object2IntLinkedOpenHashMap<DefaultMutableTreeNode>()
    for ((i, newChild) in newChildren.withIndex()) {
      val oldChild = findOldByNew(newChild)
      if (oldChild != null) {
        updatedWithIndices[newChild] = i
      }
    }
    return updatedWithIndices
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
    if (removedChildCount == 0) return
    val removedChildren = mutableListOf<DefaultMutableTreeNode>()
    for (i in removedChildCount - 1 downTo 0) {
      removedChildren += parent.getChildAt(i) as DefaultMutableTreeNode
      parent.remove(i)
    }
    delegate.nodesWereRemoved(parent, IntArray(removedChildCount) { it }, removedChildren.toTypedArray())
    this.cachedPresentationApplier?.nodesRemoved(removedChildren)
  }

  private fun removeChildrenFromDelegate(
    parent: DefaultMutableTreeNode,
    childrenWithIndices: Object2IntMap<DefaultMutableTreeNode>,
  ) {
    if (childrenWithIndices.isEmpty()) return
    val childIndices = childrenWithIndices.values.toIntArray()
    for (i in childIndices.reversed()) { // reversed so that previously removed children won't affect to-be-removed indices
      parent.remove(i)
    }
    val removedChildren = childrenWithIndices.keys.toTypedArray()
    delegate.nodesWereRemoved(parent, childIndices, removedChildren)
    this.cachedPresentationApplier?.nodesRemoved(removedChildren.toList())
  }

  private fun insertChildrenIntoDelegate(
    parent: DefaultMutableTreeNode,
    children: List<DefaultMutableTreeNode>,
  ) {
    if (children.isEmpty()) return
    this.cachedPresentationApplier?.childrenLoaded(CachingTreePath(parent.path), children)
    for ((index, child) in children.withIndex()) {
      parent.insert(child, index)
    }
    delegate.nodesWereInserted(parent, IntArray(children.size) { it })
  }

  private fun insertChildrenIntoDelegate(
    parent: DefaultMutableTreeNode,
    childrenWithIndices: Object2IntMap<DefaultMutableTreeNode>,
  ) {
    if (childrenWithIndices.isEmpty()) return
    if (parent.childCount == 0) { // Not strictly necessary, as it's only needed for the first time, and the first time it's always true.
      this.cachedPresentationApplier?.childrenLoaded(CachingTreePath(parent.path), childrenWithIndices.keys.toList())
    }
    for (entry in childrenWithIndices.object2IntEntrySet()) {
      parent.insert(entry.key, entry.intValue)
    }
    delegate.nodesWereInserted(parent, childrenWithIndices.values.toIntArray())
  }
  
  private fun updateChildrenInDelegate(
    parent: DefaultMutableTreeNode,
    childrenWithIndices: Object2IntMap<DefaultMutableTreeNode>,
  ) {
    if (childrenWithIndices.isEmpty()) return
    val childIndices = childrenWithIndices.values.toIntArray()
    for (entry in childrenWithIndices.object2IntEntrySet()) {
      (parent.getChildAt(entry.intValue) as DefaultMutableTreeNode).userObject = entry.key.userObject
    }
    delegate.nodesChanged(parent, childIndices)
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

private val DefaultMutableTreeNode.isCached: Boolean
  get() = userObject is CachedTreePresentationNode

private val LOG = logger<DefaultTreeModelWithCachedPresentation>()
