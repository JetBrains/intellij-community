// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.customization

import com.intellij.ide.IdeBundle
import com.intellij.ide.ui.customization.CustomizableActionsPanel.IconInfo
import com.intellij.ide.ui.customization.CustomizableActionsPanel.NONE
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.ex.QuickListsManager
import com.intellij.openapi.keymap.impl.ui.ActionsTreeUtil
import com.intellij.openapi.keymap.impl.ui.Group
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.ComponentValidator
import com.intellij.openapi.ui.DialogPanel
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
import javax.swing.JComponent
import javax.swing.JTree
import javax.swing.MutableComboBoxModel
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
    addTreeSelectionListener { e ->
      editComboboxModelForPath(e.path)
      val selectedNode = e.path.lastPathComponent as DefaultMutableTreeNode
      val (actionId, icon) = getActionIdAndIcon(selectedNode)
      if (actionId != null && icon != null) {
        val selected = browseComboBox.selectByCondition { info -> info.iconReference == actionId }
                       || browseComboBox.selectByCondition { info -> info.icon == icon }
        if (selected) {
          return@addTreeSelectionListener
        }
      }
      browseComboBox.selectedIndex = 0
    }
  }

  private val browseComboBox: ComboBox<IconInfo> = CustomizableActionsPanel.createBrowseIconsComboBox(disposable)

  private val selectedIcon: IconInfo?
    get() = browseComboBox.selectedItem as? IconInfo
  private val selectedTreePath: TreePath?
    get() = actionsTree.selectionPath

  init {
    title = IdeBundle.message("action.choose.actions.to.add")
    init()
  }

  override fun createCenterPanel(): JComponent {
    val filterComponent = CustomizableActionsPanel.setupFilterComponent(actionsTree)
    val panel: DialogPanel = panel {
      row {
        cell(filterComponent)
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
    panel.preferredFocusedComponent = filterComponent.textEditor
    return panel
  }

  override fun doOKAction() {
    val validatorOpt = ComponentValidator.getInstance(browseComboBox)
    if (validatorOpt.isPresent) {
      val validator = validatorOpt.get()
      validator.revalidate()
      if (validator.validationInfo != null) {
        browseComboBox.requestFocusInWindow()
        return
      }
    }

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

  private fun editComboboxModelForPath(path: TreePath) {
    val groupNode = path.getPathComponent(1) as DefaultMutableTreeNode
    val group = groupNode.userObject as? Group
    val model = browseComboBox.model as MutableComboBoxModel
    val firstInfo = model.getElementAt(0)
    if (group?.id == IdeActions.GROUP_MAIN_MENU && firstInfo != NONE) {
      model.insertElementAt(NONE, 0)
    }
    else if (group?.id != IdeActions.GROUP_MAIN_MENU && firstInfo == NONE) {
      model.removeElementAt(0)
    }
  }

  private fun getActionIdAndIcon(node: DefaultMutableTreeNode): kotlin.Pair<String?, Icon?> {
    return when (val obj = node.userObject) {
      is String -> obj to ActionManager.getInstance().getAction(obj)?.templatePresentation?.icon
      is Group -> obj.id to obj.icon
      is Pair<*, *> -> obj.first as? String to obj.second as? Icon
      else -> null to null
    }
  }

  private fun ComboBox<IconInfo>.selectByCondition(predicate: (IconInfo) -> Boolean): Boolean {
    val ind = (0 until model.size).find { predicate(model.getElementAt(it)) }
    return (ind != null).also {
      if (it) browseComboBox.selectedIndex = ind!!
    }
  }

  override fun getDimensionServiceKey() = "#com.intellij.ide.ui.customization.CustomizableActionsPanel.FindAvailableActionsDialog"
}