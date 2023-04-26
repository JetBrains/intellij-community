// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util

import com.intellij.ide.DataManager
import com.intellij.ide.util.treeView.NodeDescriptor
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.util.Key
import com.intellij.ui.ClientProperty
import com.intellij.ui.DoubleClickListener
import com.intellij.ui.treeStructure.treetable.TreeTable
import com.intellij.util.ui.tree.ExpandOnDoubleClick
import com.intellij.util.ui.tree.TreeUtil
import java.awt.Component
import java.awt.event.MouseEvent
import javax.swing.JList
import javax.swing.JTable
import javax.swing.JTree
import javax.swing.SwingUtilities
import javax.swing.tree.TreePath

object EditSourceOnDoubleClickHandler {
  private val INSTALLED = Key.create<Boolean>("EditSourceOnDoubleClickHandlerInstalled")

  @JvmOverloads
  @JvmStatic
  fun install(tree: JTree, whenPerformed: Runnable? = null) {
    TreeMouseListener(tree, whenPerformed).installOn(tree)
  }

  @JvmStatic
  fun install(treeTable: TreeTable) {
    object : DoubleClickListener() {
      override fun onDoubleClick(e: MouseEvent): Boolean {
        if (ModalityState.current().dominates(ModalityState.NON_MODAL) || treeTable.tree.getPathForLocation(e.x, e.y) == null) {
          return false
        }

        val dataContext = DataManager.getInstance().getDataContext(treeTable)
        CommonDataKeys.PROJECT.getData(dataContext) ?: return false
        OpenSourceUtil.openSourcesFrom(dataContext, true)
        return true
      }
    }.installOn(treeTable)
  }

  @JvmStatic
  fun install(table: JTable) {
    object : DoubleClickListener() {
      override fun onDoubleClick(e: MouseEvent): Boolean {
        if (ModalityState.current().dominates(ModalityState.NON_MODAL) ||
            table.columnAtPoint(e.point) < 0 ||
            table.rowAtPoint(e.point) < 0) {
          return false
        }

        val dataContext = DataManager.getInstance().getDataContext(table)
        CommonDataKeys.PROJECT.getData(dataContext) ?: return false
        OpenSourceUtil.openSourcesFrom(dataContext, true)
        return true
      }
    }.installOn(table)
  }

  @JvmOverloads
  @JvmStatic
  fun install(list: JList<*>, whenPerformed: Runnable? = null) {
    object : DoubleClickListener() {
      override fun onDoubleClick(e: MouseEvent): Boolean {
        val point = e.point
        val index = list.locationToIndex(point)
        if (index == -1 || !list.getCellBounds(index, index).contains(point)) {
          return false
        }

        val dataContext = DataManager.getInstance().getDataContext(list)
        OpenSourceUtil.openSourcesFrom(dataContext, true)
        whenPerformed?.run()
        return true
      }
    }.installOn(list)
  }

  @JvmStatic
  fun isToggleEvent(tree: JTree, e: MouseEvent): Boolean {
    if (!SwingUtilities.isLeftMouseButton(e)) {
      return false
    }

    val count = tree.toggleClickCount
    if (count <= 0 || e.clickCount % count != 0) {
      return false
    }
    else {
      return isExpandPreferable(tree, tree.selectionPath)
    }
  }

  /**
   * @return `true` to expand/collapse the node, `false` to navigate to source if possible
   */
  @JvmStatic
  fun isExpandPreferable(tree: JTree, path: TreePath?): Boolean {
    if (path == null) {
      // path is not specified
      return false
    }

    val behavior = ExpandOnDoubleClick.getBehavior(tree)
    if (behavior == ExpandOnDoubleClick.NEVER) {
      // disable expand/collapse
      return false
    }

    val model = tree.model
    if (model == null || model.isLeaf(path.lastPathComponent)) {
      return false
    }

    if (!ClientProperty.isTrue(tree, INSTALLED)) {
      // expand by default if handler is not installed
      return true
    }

    // navigate to source is preferred if the tree provides a navigatable object for the given path
    if (behavior == ExpandOnDoubleClick.NAVIGATABLE) {
      val navigatable = TreeUtil.getNavigatable(tree, path)
      if (navigatable != null && navigatable.canNavigateToSource()) {
        return false
      }
    }

    if (behavior == ExpandOnDoubleClick.ALWAYS) {
      return true
    }

    // for backward compatibility
    val descriptor = TreeUtil.getLastUserObject(NodeDescriptor::class.java, path)
    return descriptor == null || descriptor.expandOnDoubleClick()
  }

  open class TreeMouseListener @JvmOverloads constructor(private val tree: JTree,
                                                         private val whenPerformed: Runnable? = null) : DoubleClickListener() {
    override fun installOn(c: Component, allowDragWhileClicking: Boolean) {
      super.installOn(c, allowDragWhileClicking)
      tree.putClientProperty(INSTALLED, true)
    }

    override fun uninstall(c: Component) {
      super.uninstall(c)
      tree.putClientProperty(INSTALLED, null)
    }

    public override fun onDoubleClick(e: MouseEvent): Boolean {
      @Suppress("DEPRECATION")
      val clickPath = (if (com.intellij.util.ui.tree.WideSelectionTreeUI.isWideSelection(tree)) {
        tree.getClosestPathForLocation(e.x, e.y)
      }
      else {
        tree.getPathForLocation(e.x, e.y)
      }) ?: return false
      val dataContext = DataManager.getInstance().getDataContext(tree)
      CommonDataKeys.PROJECT.getData(dataContext) ?: return false
      val selectionPath = tree.selectionPath
      if (clickPath != selectionPath) {
        return false
      }

      // node expansion for non-leafs has a higher priority
      if (isToggleEvent(tree, e)) {
        return false
      }
      processDoubleClick(e = e, dataContext = dataContext, treePath = selectionPath)
      return true
    }

    protected open fun processDoubleClick(e: MouseEvent, dataContext: DataContext, treePath: TreePath) {
      SlowOperations.knownIssue("IDEA-304701, EA-659716").use {
        OpenSourceUtil.openSourcesFrom(dataContext, true)
      }
      whenPerformed?.run()
    }
  }
}
