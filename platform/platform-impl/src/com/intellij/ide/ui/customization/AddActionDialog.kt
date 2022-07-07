// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.customization

import com.intellij.ide.IdeBundle
import com.intellij.ide.ui.customization.CustomizableActionsPanel.IconInfo
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ex.QuickListsManager
import com.intellij.openapi.keymap.impl.ui.ActionsTreeUtil
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Pair
import com.intellij.ui.dsl.builder.EMPTY_LABEL
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.Gaps
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import javax.swing.Icon
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath

internal class AddActionDialog(private val customActionsSchema: CustomActionsSchema) : DialogWrapper(false) {
  private val actionsTree: JTree = Tree().apply {
    val rootGroup = ActionsTreeUtil.createMainGroup(null, null, QuickListsManager.getInstance().allQuickLists)
    val root = ActionsTreeUtil.createNode(rootGroup)
    this.model = DefaultTreeModel(root)
    isRootVisible = false
    cellRenderer = CustomizableActionsPanel.createDefaultRenderer()
  }

  private val browseComboBox: ComboBox<IconInfo> = CustomizableActionsPanel.createBrowseIconsComboBox()

  private val selectedIcon: IconInfo?
    get() = browseComboBox.selectedItem as? IconInfo
  private val selectedTreePath: TreePath?
    get() = actionsTree.selectionPath

  init {
    title = IdeBundle.message("action.choose.actions.to.add")
    init()
  }

  override fun createCenterPanel() = panel {
    row {
      cell(CustomizableActionsPanel.setupFilterComponent(actionsTree))
        .horizontalAlign(HorizontalAlign.FILL)
    }
    row {
      scrollCell(actionsTree)
        .horizontalAlign(HorizontalAlign.FILL)
    }
    row(IdeBundle.message("label.icon.path")) {
      cell(browseComboBox)
        .horizontalAlign(HorizontalAlign.FILL)
        .customize(Gaps.EMPTY)
    }
    row(EMPTY_LABEL) {
      label(IdeBundle.message("browse.custom.icon.hint"))
        .applyToComponent {
          font = JBUI.Fonts.smallFont()
          foreground = UIUtil.getLabelInfoForeground()
        }.customize(Gaps.EMPTY)
    }
  }

  override fun doOKAction() {
    val iconInfo = selectedIcon
    val selectedNode = selectedTreePath?.lastPathComponent as? DefaultMutableTreeNode
    if (iconInfo != null && selectedNode != null) {
      CustomizableActionsPanel.doSetIcon(customActionsSchema, selectedNode, iconInfo.iconReference, contentPane)

      val userObject = selectedNode.userObject
      if (userObject is Pair<*, *>) {
        val actionId = userObject.first as String
        val action = ActionManager.getInstance().getAction(actionId)
        val icon = userObject.second as? Icon
        action.templatePresentation.icon = icon
        action.isDefaultIcon = icon == null
      }
    }
    super.doOKAction()
  }

  fun getAddedActionInfo(): Any? {
    val iconInfo = selectedIcon
    val selectedNode = selectedTreePath?.lastPathComponent as? DefaultMutableTreeNode
    return selectedNode?.userObject?.takeIf { iconInfo != null }
  }

  override fun getDimensionServiceKey() = "#com.intellij.ide.ui.customization.CustomizableActionsPanel.FindAvailableActionsDialog"
}