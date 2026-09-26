// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide

import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.tree.TreeUtil
import org.jetbrains.annotations.ApiStatus
import javax.swing.JTree
import javax.swing.tree.TreePath

open class DefaultTreeExpander(private val supplier: () -> JTree?) : TreeExpander {

  constructor(tree: JTree) : this({ tree })


  override fun canExpand(): Boolean = supplier()?.let { canExpand(it) } ?: false

  protected open fun canExpand(tree: JTree): Boolean = isEnabled(tree)


  override fun expandAll() {
    supplier()?.let { expandAll(it) }
  }

  protected open fun expandAll(tree: JTree) {
    TreeUtil.promiseExpandAll(tree).onSuccess { showSelectionCentered(tree) }
  }

  override fun expandSelected() {
    expandSelected(supplier() ?: return)
  }

  protected open fun expandSelected(tree: JTree) {
    val selection = tree.selectionPaths ?: return
    TreeUtil.promiseExpandRecursively(tree, *selection)
  }

  override fun canExpandSelected(): Boolean {
    return supplier()?.let { canExpandSelected(it) } == true
  }

  protected open fun canExpandSelected(tree: JTree): Boolean = tree.selectionCount > 0

  override fun canCollapse(): Boolean = supplier()?.let { canCollapse(it) } ?: false

  protected open fun canCollapse(tree: JTree): Boolean = isEnabled(tree)


  override fun collapseAll() {
    supplier()?.let { collapseAll(it, 1) }
  }

  protected open fun collapseAll(tree: JTree, keepSelectionLevel: Int) {
    collapseAll(tree, true, keepSelectionLevel)
  }

  protected open fun collapseAll(tree: JTree, strict: Boolean, keepSelectionLevel: Int) {
    TreeUtil.collapseAll(tree, strict, keepSelectionLevel)
    showSelectionCentered(tree)
  }


  protected open fun isEnabled(tree: JTree): Boolean = isShowing(tree) && tree.rowCount > 0

  protected open fun isShowing(tree: JTree): Boolean = UIUtil.isShowing(tree)

  protected open fun showSelectionCentered(tree: JTree) {
    tree.selectionPath?.let { TreeUtil.scrollToVisible(tree, it, true) }
  }
}

@get:ApiStatus.Internal
val TreePath.isIncludedInExpandAll: Boolean
  get() {
    // Include by default, unless the node can and does tell us otherwise.
    val node = TreeUtil.getLastUserObject(this) as? AbstractTreeNode<*> ?: return true
    return node.isIncludedInExpandAll
  }
