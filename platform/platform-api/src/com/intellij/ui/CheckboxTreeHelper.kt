// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import com.intellij.openapi.util.Key
import com.intellij.ui.CheckboxTreeBase.CheckPolicy
import com.intellij.ui.CheckboxTreeBase.CheckboxTreeCellRendererBase
import com.intellij.ui.speedSearch.SpeedSearchSupply
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ArrayUtil
import com.intellij.util.EventDispatcher
import com.intellij.util.ui.tree.TreeUtil
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.KeyListener
import java.awt.event.MouseEvent
import java.util.*
import javax.swing.JComponent
import javax.swing.tree.TreeModel
import javax.swing.tree.TreePath

class CheckboxTreeHelper(private val myCheckPolicy: CheckPolicy, private val myEventDispatcher: EventDispatcher<CheckboxTreeListener?>) {
  fun initTree(tree: Tree, mainComponent: JComponent, cellRenderer: CheckboxTreeCellRendererBase) {
    removeTreeListeners(mainComponent)
    tree.setCellRenderer(cellRenderer)
    tree.setRootVisible(false)
    tree.setShowsRootHandles(true)
    TreeUtil.installActions(tree)

    val keyListener = setupKeyListener(tree, mainComponent)
    val clickListener = setupMouseListener(tree, mainComponent, cellRenderer)
    ComponentUtil.putClientProperty(mainComponent, TREE_LISTENERS_REMOVER, Runnable {
      mainComponent.removeKeyListener(keyListener)
      clickListener.uninstall(mainComponent)
    })
  }

  fun setNodeState(tree: Tree, node: CheckedTreeNode, checked: Boolean) {
    changeNodeState(node, checked)
    adjustParentsAndChildren(node, checked)
    tree.repaint()

    // notify model listeners about model change
    val model = tree.model
    model.valueForPathChanged(TreePath(node.path), node.getUserObject())
  }

  private fun toggleNode(tree: Tree, node: CheckedTreeNode) {
    setNodeState(tree, node, !node.isChecked())
  }

  private fun adjustParentsAndChildren(node: CheckedTreeNode, checked: Boolean) {
    if (!checked) {
      if (myCheckPolicy.uncheckParentWithUncheckedChild) {
        var parent = node.getParent()
        while (parent != null) {
          if (parent is CheckedTreeNode) {
            changeNodeState(parent, false)
          }
          parent = parent.parent
        }
      }
      if (myCheckPolicy.uncheckChildrenWithUncheckedParent) {
        uncheckChildren(node)
      }
    }
    else {
      if (myCheckPolicy.checkChildrenWithCheckedParent) {
        checkChildren(node)
      }

      if (myCheckPolicy.checkParentWithCheckedChild) {
        var parent = node.getParent()
        while (parent != null) {
          if (parent is CheckedTreeNode) {
            changeNodeState(parent, true)
          }
          parent = parent.parent
        }
      }
    }
  }

  private fun changeNodeState(node: CheckedTreeNode, checked: Boolean) {
    if (node.isChecked() != checked) {
      myEventDispatcher.getMulticaster().beforeNodeStateChanged(node)
      node.setChecked(checked)
      myEventDispatcher.getMulticaster().nodeStateChanged(node)
    }
  }

  private fun uncheckChildren(node: CheckedTreeNode) {
    val children: Enumeration<*> = node.children()
    while (children.hasMoreElements()) {
      val o: Any? = children.nextElement()
      if (o !is CheckedTreeNode) continue
      changeNodeState(o, false)
      uncheckChildren(o)
    }
  }

  private fun checkChildren(node: CheckedTreeNode) {
    val children: Enumeration<*> = node.children()
    while (children.hasMoreElements()) {
      val o: Any? = children.nextElement()
      if (o !is CheckedTreeNode) continue
      changeNodeState(o, true)
      checkChildren(o)
    }
  }

  private fun setupKeyListener(tree: Tree, mainComponent: JComponent): KeyListener {
    val listener: KeyListener = object : KeyAdapter() {
      override fun keyPressed(e: KeyEvent) {
        if (isToggleEvent(e, mainComponent)) {
          val selectionPaths = tree.getSelectionPaths()
          if (selectionPaths == null || selectionPaths.size == 0) return

          var treePath = tree.leadSelectionPath
          if (treePath == null) return

          var nodesToChange = selectionPaths.size - 1
          if (!tree.isPathSelected(treePath)) {
            treePath = selectionPaths[nodesToChange]
            nodesToChange--
          }

          val o = treePath.lastPathComponent
          if (o !is CheckedTreeNode) return
          if (!o.isEnabled) return
          toggleNode(tree, o)
          val checked = o.isChecked()

          for (i in 0..nodesToChange) {
            val selectionPath = selectionPaths[i]
            val o1 = selectionPath.lastPathComponent
            if (o1 !is CheckedTreeNode) continue
            setNodeState(tree, o1, checked)
          }

          e.consume()
        }
      }
    }
    mainComponent.addKeyListener(listener)
    return listener
  }

  private fun setupMouseListener(tree: Tree, mainComponent: JComponent, cellRenderer: CheckboxTreeCellRendererBase): ClickListener {
    val listener: ClickListener = object : ClickListener() {
      override fun onClick(e: MouseEvent, clickCount: Int): Boolean {
        val row = tree.getRowForLocation(e.getX(), e.getY())
        if (row < 0) return false
        val o = tree.getPathForRow(row).lastPathComponent
        if (o !is CheckedTreeNode) return false
        val rowBounds = tree.getRowBounds(row)
        cellRenderer.bounds = rowBounds
        cellRenderer.validate()
        val checkBounds = cellRenderer.myCheckbox.bounds
        checkBounds.location = rowBounds.location

        if (checkBounds.height == 0) {
          checkBounds.width = rowBounds.height
          checkBounds.height = checkBounds.width
        }

        val clickableArea = if (myCheckPolicy.checkByRowClick) rowBounds else checkBounds
        if (clickableArea.contains(e.getPoint()) && cellRenderer.myCheckbox.isVisible) {
          if (o.isEnabled) {
            toggleNode(tree, o)
            tree.setSelectionRow(row)
            return true
          }
        }
        else if (clickCount > 1 && clickCount % 2 == 0) {
          myEventDispatcher.getMulticaster().mouseDoubleClicked(o)
          return true
        }

        return false
      }
    }
    listener.installOn(mainComponent)
    return listener
  }

  private fun removeTreeListeners(mainComponent: JComponent) {
    ComponentUtil.getClientProperty(mainComponent, TREE_LISTENERS_REMOVER)?.run()
  }

  companion object {
    private val TREE_LISTENERS_REMOVER = Key.create<Runnable?>("TREE_LISTENERS_REMOVER")
    @Deprecated("This value has been special-cased to retain a defect, and should not be used for new code. See `PROPAGATE_EVERYTHING_POLICY` .see IJPL-199505")
    @JvmField
    val DEFAULT_POLICY: CheckPolicy = CheckPolicy(true, true, false, true)
    @JvmStatic
    fun isToggleEvent(e: KeyEvent, mainComponent: JComponent): Boolean {
      return e.getKeyCode() == KeyEvent.VK_SPACE && SpeedSearchSupply.getSupply(mainComponent) == null
    }

    @JvmStatic
    fun <T> getCheckedNodes(nodeType: Class<out T>, filter: Tree.NodeFilter<in T>?, model: TreeModel): Array<T> {
      val nodes = ArrayList<T?>()
      val root = model.root
      check(
        root is CheckedTreeNode) { "The root must be instance of the " + CheckedTreeNode::class.java.getName() + ": " + root.javaClass.getName() }
      object : Any() {
        fun collect(node: CheckedTreeNode) {
          if (node.isLeaf) {
            val userObject = node.getUserObject()
            if (node.isChecked() && userObject != null && nodeType.isAssignableFrom(userObject.javaClass)) {
              @Suppress("UNCHECKED_CAST")
              val value = userObject as T?
              if (filter != null && !filter.accept(value)) return
              nodes.add(value)
            }
          }
          else {
            for (i in 0..<node.childCount) {
              val child = node.getChildAt(i)
              if (child is CheckedTreeNode) {
                collect(child)
              }
            }
          }
        }
      }.collect(root)
      return nodes.toArray(ArrayUtil.newArray(nodeType, nodes.size))
    }
  }
}
