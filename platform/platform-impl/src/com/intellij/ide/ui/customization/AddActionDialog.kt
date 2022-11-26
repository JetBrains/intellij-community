// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.customization

import com.intellij.ide.IdeBundle
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.actionSystem.ex.QuickListsManager
import com.intellij.openapi.keymap.impl.ui.ActionsTreeUtil
import com.intellij.openapi.ui.ComponentValidator
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Pair
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.Gaps
import com.intellij.ui.layout.ComponentPredicate
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import javax.swing.JComponent
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath

internal class AddActionDialog(private val customActionsSchema: CustomActionsSchema,
                               withNoneItem: Boolean) : DialogWrapper(false) {
  private val actionsTree: JTree = Tree().apply {
    val rootGroup = ActionsTreeUtil.createMainGroup(null, null, QuickListsManager.getInstance().allQuickLists,
                                                    null, true) { action -> action !is Separator }
    val root = ActionsTreeUtil.createNode(rootGroup)
    this.model = DefaultTreeModel(root)
    isRootVisible = false
    cellRenderer = CustomizableActionsPanel.createDefaultRenderer()
    addTreeSelectionListener { e -> browseComboBox.selectIconForNode(e.path.lastPathComponent as DefaultMutableTreeNode) }
  }

  private val browseComboBox = BrowseIconsComboBox(customActionsSchema, disposable, withNoneItem)

  private val selectedIcon: ActionIconInfo?
    get() = browseComboBox.selectedItem as? ActionIconInfo
  private val selectedTreePaths: Array<TreePath>
    get() = actionsTree.selectionPaths ?: emptyArray()

  init {
    title = IdeBundle.message("action.choose.actions.to.add")
    init()
  }

  override fun createCenterPanel(): JComponent {
    val filterComponent = CustomizableActionsPanel.setupFilterComponent(actionsTree)
    val panel: DialogPanel = panel {
      row {
        cell(filterComponent)
          .align(AlignX.FILL)
      }
      row {
        resizableRow()
        scrollCell(actionsTree)
          .align(Align.FILL)
      }
      row(IdeBundle.message("label.icon.path")) {
        cell(browseComboBox)
          .align(AlignX.FILL)
          .customize(Gaps.EMPTY)
          .enabledIf(object : ComponentPredicate() {
            override fun invoke(): Boolean = selectedTreePaths.size < 2

            override fun addListener(listener: (Boolean) -> Unit) {
              actionsTree.addTreeSelectionListener { listener(selectedTreePaths.size < 2) }
            }
          })
      }
      row("") {
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

    val selectedPaths = selectedTreePaths
    if (selectedPaths.size == 1) {
      val iconInfo = selectedIcon
      val selectedNode = selectedPaths[0].lastPathComponent as? DefaultMutableTreeNode
      if (iconInfo != null && selectedNode != null) {
        CustomizableActionsPanel.setCustomIcon(customActionsSchema, selectedNode, iconInfo, contentPane)
        if (selectedNode.userObject is Pair<*, *>) {
          val actionId = CustomizableActionsPanel.getActionId(selectedNode)
          if (actionId != null) {
            val action = ActionManager.getInstance().getAction(actionId)
            action.templatePresentation.icon = iconInfo.icon
            action.isDefaultIcon = iconInfo.icon == null
          }
        }
      }
    }
    super.doOKAction()
  }

  fun getAddedActions(): List<Any?> {
    return selectedTreePaths.mapNotNull { path ->
      val node = path.lastPathComponent as? DefaultMutableTreeNode
      node?.userObject?.let { if (it is Pair<*, *>) it.first else it }
    }
  }

  override fun getDimensionServiceKey() = "#com.intellij.ide.ui.customization.CustomizableActionsPanel.FindAvailableActionsDialog"
}