// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.EventDispatcher
import com.intellij.util.ui.ThreeStateCheckBox
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.accessibility.AccessibleContextDelegateWithContextMenu
import com.intellij.util.ui.accessibility.AccessibleContextUtil
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Container
import javax.accessibility.AccessibleContext
import javax.swing.JCheckBox
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeCellRenderer

open class CheckboxTreeBase @JvmOverloads constructor(
  cellRenderer: CheckboxTreeCellRendererBase = CheckboxTreeCellRendererBase(),
  root: CheckedTreeNode? = null,
  checkPolicy: CheckPolicy = CheckboxTreeHelper.DEFAULT_POLICY,
) : Tree() {
  private val myHelper: CheckboxTreeHelper
  private val myEventDispatcher = EventDispatcher.create(CheckboxTreeListener::class.java)

  init {
    myHelper = CheckboxTreeHelper(checkPolicy, myEventDispatcher)
    if (root != null) {
      // override default model ("colors", etc.) ASAP to avoid CCE in renderers
      setModel(DefaultTreeModel(root))
      setSelectionRow(0)
    }
    myEventDispatcher.addListener(object : CheckboxTreeListener {
      override fun mouseDoubleClicked(node: CheckedTreeNode) {
        onDoubleClick(node)
      }

      override fun nodeStateChanged(node: CheckedTreeNode) {
        this@CheckboxTreeBase.onNodeStateChanged(node)
      }

      override fun beforeNodeStateChanged(node: CheckedTreeNode) {
        this@CheckboxTreeBase.nodeStateWillChange(node)
      }
    })
    myHelper.initTree(this, this, cellRenderer)
  }

  open fun setNodeState(node: CheckedTreeNode, checked: Boolean) {
    myHelper.setNodeState(this, node, checked)
  }

  fun addCheckboxTreeListener(listener: CheckboxTreeListener) {
    myEventDispatcher.addListener(listener)
  }

  protected open fun onDoubleClick(node: CheckedTreeNode?) {
  }

  /**
   * Collect checked leaf nodes of the type `nodeType` and that are accepted by
   * `filter`
   *
   * @param nodeType the type of userobject to consider
   * @param filter   the filter (if null all nodes are accepted)
   * @param <T>      the type of the node
   * @return an array of collected nodes
  </T> */
  open fun <T> getCheckedNodes(nodeType: Class<out T>, filter: NodeFilter<in T>?): Array<T> {
    return CheckboxTreeHelper.getCheckedNodes(nodeType, filter, model)
  }

  override fun getToggleClickCount(): Int {
    // to prevent node expanding/collapsing on checkbox toggling
    return -1
  }

  protected open fun onNodeStateChanged(node: CheckedTreeNode?) {
  }

  protected open fun nodeStateWillChange(node: CheckedTreeNode?) {
  }

  open class CheckboxTreeCellRendererBase @JvmOverloads constructor(
    opaque: Boolean = true,
    private val myUsePartialStatusForParentNodes: Boolean = true,
  ) : JPanel(
    BorderLayout()), TreeCellRenderer {
    open val textRenderer: ColoredTreeCellRenderer = object : ColoredTreeCellRenderer() {
      override fun customizeCellRenderer(
        tree: JTree,
        value: Any?,
        selected: Boolean,
        expanded: Boolean,
        leaf: Boolean,
        row: Int,
        hasFocus: Boolean,
      ) {
      }
    }

    @JvmField
    val myCheckbox: ThreeStateCheckBox = ThreeStateCheckBox()

    @JvmField
    protected var myIgnoreInheritance: Boolean = false

    init {
      myCheckbox.isSelected = false
      myCheckbox.isThirdStateEnabled = false
      textRenderer.setOpaque(opaque)
      add(myCheckbox, BorderLayout.WEST)
      add(textRenderer, BorderLayout.CENTER)
    }

    override fun getTreeCellRendererComponent(
      tree: JTree,
      value: Any,
      selected: Boolean,
      expanded: Boolean,
      leaf: Boolean,
      row: Int,
      hasFocus: Boolean,
    ): Component {
      invalidate()
      if (value is CheckedTreeNode) {
        val state = getNodeStatus(value)
        myCheckbox.isVisible = true
        myCheckbox.setEnabled(value.isEnabled)
        myCheckbox.isSelected = state != ThreeStateCheckBox.State.NOT_SELECTED
        myCheckbox.setState(state)
        myCheckbox.setOpaque(false)
        myCheckbox.setBackground(null)
        setBackground(null)

        if (UIUtil.isUnderWin10LookAndFeel()) {
          val hoverValue = getClientProperty(UIUtil.CHECKBOX_ROLLOVER_PROPERTY)
          myCheckbox.getModel().isRollover = hoverValue === value

          val pressedValue = getClientProperty(UIUtil.CHECKBOX_PRESSED_PROPERTY)
          myCheckbox.getModel().isPressed = pressedValue === value
        }
      }
      else {
        myCheckbox.isVisible = false
      }
      textRenderer.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus)

      customizeRenderer(tree, value, selected, expanded, leaf, row, hasFocus)
      revalidate()

      return this
    }

    private fun getNodeStatus(node: CheckedTreeNode): ThreeStateCheckBox.State {
      val ownState = if (node.isChecked()) ThreeStateCheckBox.State.SELECTED else ThreeStateCheckBox.State.NOT_SELECTED
      if (myIgnoreInheritance || node.childCount == 0 || !myUsePartialStatusForParentNodes) {
        return ownState
      }

      var result: ThreeStateCheckBox.State? = null
      for (i in 0..<node.childCount) {
        val child = node.getChildAt(i)
        val childStatus = if (child is CheckedTreeNode) getNodeStatus(child) else ownState
        if (childStatus == ThreeStateCheckBox.State.DONT_CARE) return ThreeStateCheckBox.State.DONT_CARE
        if (result == null) {
          result = childStatus
        }
        else if (result != childStatus) {
          return ThreeStateCheckBox.State.DONT_CARE
        }
      }


      // If all children have the same state but it differs from the parent's state,
      // return DONT_CARE (partial) instead of the children's state
      if (result != null && result != ownState) {
        return ThreeStateCheckBox.State.DONT_CARE
      }

      return result ?: ownState
    }

    override fun getAccessibleContext(): AccessibleContext {
      if (accessibleContext == null) {
        accessibleContext = object : AccessibleContextDelegateWithContextMenu(super.getAccessibleContext()) {
          override fun getDelegateParent(): Container? {
            return getParent()
          }

          override fun doShowContextMenu() {
            ActionManager.getInstance().tryToExecute(ActionManager.getInstance().getAction("ShowPopupMenu"), null, null, null, true)
          }

          override fun getAccessibleName(): String? {
            return AccessibleContextUtil.combineAccessibleStrings(
              textRenderer.getAccessibleContext().getAccessibleName(),
              UIBundle.message(
                if (myCheckbox.isSelected) "checkbox.tree.accessible.name.checked" else "checkbox.tree.accessible.name.not.checked"))
          }
        }
      }
      return accessibleContext
    }

    /**
     * Should be implemented by concrete implementations.
     * This method is invoked only for customization of component.
     * All component attributes are cleared when this method is being invoked.
     * Note that in general case `value` is not an instance of CheckedTreeNode.
     */
    open fun customizeRenderer(
      tree: JTree,
      value: Any,
      selected: Boolean,
      expanded: Boolean,
      leaf: Boolean,
      row: Int,
      hasFocus: Boolean,
    ) {
    }

    val checkbox: JCheckBox
      get() = myCheckbox
  }

  open class CheckPolicy @JvmOverloads constructor(
    @JvmField val checkChildrenWithCheckedParent: Boolean,
    @JvmField val uncheckChildrenWithUncheckedParent: Boolean,
    @JvmField val checkParentWithCheckedChild: Boolean,
    @JvmField val uncheckParentWithUncheckedChild: Boolean,
    @JvmField val checkByRowClick: Boolean = false,
  )
}
