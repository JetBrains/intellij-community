// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.tree

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.ui.SwingActionDelegate
import com.intellij.util.ui.tree.EditableNode
import com.intellij.util.ui.tree.EditableTree
import com.intellij.util.ui.tree.TreeUtil
import javax.swing.JTree

internal class StartEditingAction : DumbAwareAction() {
  private val AnActionEvent.contextTree
    get() = getData(PlatformCoreDataKeys.CONTEXT_COMPONENT) as? JTree

  private fun getEditableNode(node: Any?) = node as? EditableNode
                                            ?: TreeUtil.getUserObject(EditableNode::class.java, node)

  private fun getEditableTree(tree: JTree) = tree.model as? EditableTree
                                             ?: tree as? EditableTree
                                             ?: tree.getClientProperty(EditableTree.KEY) as? EditableTree

  override fun update(event: AnActionEvent) {
    event.presentation.isEnabledAndVisible = false
    val tree = event.contextTree ?: return
    event.presentation.isVisible = true
    // enable editing if the selected path is editable
    val path = tree.leadSelectionPath ?: return
    event.presentation.isEnabled = tree.run { isPathEditable(path) && cellEditor?.isCellEditable(null) == true }
    // update action presentation according to the selected node
    getEditableNode(path.lastPathComponent)?.updateAction(event.presentation)
    ?: getEditableTree(tree)?.updateAction(event.presentation, path)
  }

  override fun actionPerformed(event: AnActionEvent) {
    // javax.swing.plaf.basic.BasicTreeUI.Actions.START_EDITING
    SwingActionDelegate.performAction("startEditing", event.contextTree)
  }

  init {
    isEnabledInModalContext = true
  }
}
