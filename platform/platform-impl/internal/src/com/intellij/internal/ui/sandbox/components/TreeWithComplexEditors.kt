// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.ui.sandbox.components

import com.intellij.internal.ui.sandbox.UISandboxPanel
import com.intellij.openapi.Disposable
import com.intellij.ui.ClientProperty
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.render.RenderingHelper
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.tree.TreeUtil
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.AbstractCellEditor
import javax.swing.GroupLayout
import javax.swing.GroupLayout.Alignment.BASELINE
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeCellEditor
import javax.swing.tree.TreeCellRenderer
import javax.swing.tree.TreeModel
import javax.swing.tree.TreeNode

internal class TreeWithComplexEditors : UISandboxPanel {

  override val title: String = "Tree with complex editors"

  override val isScrollbarNeeded: Boolean = false

  override fun createContent(disposable: Disposable): JComponent {
    val tree = Tree(createModel())
    ClientProperty.put(tree, RenderingHelper.RESIZE_EDITOR_TO_RENDERER_SIZE, true)
    tree.cellRenderer = ComplexCellRenderer()
    tree.cellEditor = ComplexCellEditor()
    tree.invokesStopCellEditing = true
    tree.isEditable = true
    
    val result = JPanel(BorderLayout())
    result.add(ScrollPaneFactory.createScrollPane(tree, true), BorderLayout.CENTER)
    return result
  }
}

private fun createModel(): TreeModel = DefaultTreeModel(createRoot())

private fun createRoot(): TreeNode = DefaultMutableTreeNode(UserObject("root")).apply {
  insert(DefaultMutableTreeNode(UserObject("child1")), 0)
  insert(DefaultMutableTreeNode(UserObject("child2")), 1)
}

private data class UserObject(
  val text: String,
  val isChecked: Boolean = false,
)

private open class ComplexCellComponent : JPanel() {
  private val textComponent = JLabel()
  private val isCheckedCheckbox = JCheckBox().apply {
    isOpaque = false
    border = JBUI.Borders.empty() // the default border is weird: it depends on whether the checkbox is in a renderer (sic!) 
  }

  var value: UserObject
    get() = UserObject(textComponent.text, isCheckedCheckbox.isSelected)
    set(value) {
      textComponent.text = value.text
      isCheckedCheckbox.isSelected = value.isChecked
    }

  init {
    isOpaque = false

    val layout = GroupLayout(this)
    val hg = layout.createSequentialGroup()
    val vg = layout.createParallelGroup(BASELINE)

    hg
      .addComponent(textComponent)
      .addGap(0, JBUI.scale(5), 99999)
      .addComponent(isCheckedCheckbox)

    vg
      .addComponent(textComponent)
      .addComponent(isCheckedCheckbox)

    layout.setHorizontalGroup(hg)
    layout.setVerticalGroup(vg)
    this.layout = layout
  }
}

private class ComplexCellRenderer : ComplexCellComponent(), TreeCellRenderer {
  override fun getTreeCellRendererComponent(tree: JTree, value: Any, selected: Boolean, expanded: Boolean, leaf: Boolean, row: Int, hasFocus: Boolean): Component {
    val userObject = TreeUtil.getUserObject(value) as UserObject
    this.value = userObject
    return this
  }
}

private class ComplexCellEditor : AbstractCellEditor(), TreeCellEditor {
  private var component: ComplexCellComponent? = null

  override fun getTreeCellEditorComponent(tree: JTree, value: Any, isSelected: Boolean, expanded: Boolean, leaf: Boolean, row: Int): Component {
    val component = this.component ?: ComplexCellComponent()
    this.component = component
    component.value = TreeUtil.getUserObject(value) as UserObject
    return component
  }

  override fun getCellEditorValue(): Any? = component?.value
}
