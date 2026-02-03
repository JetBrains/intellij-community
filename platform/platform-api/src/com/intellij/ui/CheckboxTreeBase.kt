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

open class CheckboxTreeBase(
  cellRenderer: CheckboxTreeCellRendererBase = CheckboxTreeCellRendererBase(),
  root: CheckedTreeNode? = null,
  checkPolicy: CheckPolicy,
) : Tree() {
  @JvmOverloads
  @Deprecated("provide `checkPolicy` explicitly, as the default one is defective")
  constructor(
    cellRenderer: CheckboxTreeCellRendererBase = CheckboxTreeCellRendererBase(),
    root: CheckedTreeNode? = null,
  ) : this(
    cellRenderer = cellRenderer,
    root = root,
    checkPolicy = CheckboxTreeHelper.DEFAULT_POLICY,
  )

  private val myEventDispatcher = EventDispatcher.create(CheckboxTreeListener::class.java)
  private val myHelper = CheckboxTreeHelper(checkPolicy, myEventDispatcher)
  private val myCheckPolicy = checkPolicy

  init {
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

    @Deprecated("use `threeStateCheckBox` instead", ReplaceWith("threeStateCheckBox"))
    @JvmField
    val myCheckbox: ThreeStateCheckBox = ThreeStateCheckBox()
    @Deprecated("use `threeStateCheckBox` instead", ReplaceWith("threeStateCheckBox"))
    val checkbox: JCheckBox = myCheckbox
    val threeStateCheckBox: ThreeStateCheckBox = myCheckbox

    @JvmField
    protected var myIgnoreInheritance: Boolean = false

    init {
      checkbox.isSelected = false
      myCheckbox.isThirdStateEnabled = false
      textRenderer.setOpaque(opaque)
      add(checkbox, BorderLayout.WEST)
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
        val state = getNodeStatus(value, tree as? CheckboxTreeBase)
        checkbox.isVisible = true
        checkbox.setEnabled(value.isEnabled)
        checkbox.isSelected = state != ThreeStateCheckBox.State.NOT_SELECTED
        threeStateCheckBox.setState(state)
        checkbox.setOpaque(false)
        checkbox.setBackground(null)
        setBackground(null)

        if (UIUtil.isUnderWin10LookAndFeel()) {
          val hoverValue = getClientProperty(UIUtil.CHECKBOX_ROLLOVER_PROPERTY)
          checkbox.getModel().isRollover = hoverValue === value

          val pressedValue = getClientProperty(UIUtil.CHECKBOX_PRESSED_PROPERTY)
          checkbox.getModel().isPressed = pressedValue === value
        }
      }
      else {
        checkbox.isVisible = false
      }
      textRenderer.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus)

      customizeRenderer(tree, value, selected, expanded, leaf, row, hasFocus)
      revalidate()

      return this
    }

    private fun getNodeStatus(node: CheckedTreeNode, tree: CheckboxTreeBase?): ThreeStateCheckBox.State {
      val ownState = if (node.isChecked()) ThreeStateCheckBox.State.SELECTED else ThreeStateCheckBox.State.NOT_SELECTED
      if (myIgnoreInheritance || node.childCount == 0 || !myUsePartialStatusForParentNodes) {
        return ownState
      }

      var result: ThreeStateCheckBox.State? = null
      for (i in 0..<node.childCount) {
        val child = node.getChildAt(i)
        val childStatus = if (child is CheckedTreeNode) getNodeStatus(child, tree) else ownState
        if (childStatus == ThreeStateCheckBox.State.DONT_CARE) return ThreeStateCheckBox.State.DONT_CARE
        if (result == null) {
          result = childStatus
        }
        else if (result != childStatus) {
          return ThreeStateCheckBox.State.DONT_CARE
        }
      }

      // If all children have the same state but it differs from the parent's state
      if (result != null && result != ownState) {
        // special-case: for DEFAULT_POLICY, return the children's state IJPL-199505
        if (tree != null && tree.myCheckPolicy === CheckboxTreeHelper.DEFAULT_POLICY) {
          return result
        }
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
                if (checkbox.isSelected) "checkbox.tree.accessible.name.checked" else "checkbox.tree.accessible.name.not.checked"))
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
  }

  open class CheckPolicy @JvmOverloads constructor(
    val checkChildrenWithCheckedParent: Boolean,
    val uncheckChildrenWithUncheckedParent: Boolean,
    val checkParentWithCheckedChild: Boolean,
    val uncheckParentWithUncheckedChild: Boolean,
    val checkByRowClick: Boolean = false,
  ) {
    companion object {
      @JvmField
      val PROPAGATE_EVERYTHING_POLICY: CheckPolicy = CheckPolicy(true, true, true, true)
    }
  }
}
