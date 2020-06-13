// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide

import com.intellij.util.ui.tree.TreeUtil
import javax.swing.JTree

open class DefaultTreeExpander(private val supplier: () -> JTree?) : TreeExpander {

  constructor(tree: JTree) : this({ tree })


  override fun canExpand() = supplier()?.let { canCollapse(it) } ?: false

  protected open fun canExpand(tree: JTree) = isEnabled(tree)


  override fun expandAll() {
    supplier()?.let { expandAll(it) }
  }

  protected open fun expandAll(tree: JTree) {
    TreeUtil.expandAll(tree)
    showSelectionCentered(tree)
  }


  override fun canCollapse() = supplier()?.let { canCollapse(it) } ?: false

  protected open fun canCollapse(tree: JTree) = isEnabled(tree)


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


  protected open fun isEnabled(tree: JTree) = isShowing(tree) && tree.rowCount > 0

  protected open fun isShowing(tree: JTree) = tree.isShowing

  protected open fun showSelectionCentered(tree: JTree) {
    tree.selectionPath?.let { TreeUtil.scrollToVisible(tree, it, true) }
  }
}
