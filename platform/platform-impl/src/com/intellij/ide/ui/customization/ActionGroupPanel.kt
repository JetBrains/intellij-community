// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.customization

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.QuickListsManager.Companion.getInstance
import com.intellij.openapi.keymap.KeyMapBundle
import com.intellij.openapi.keymap.impl.ui.ActionsTreeUtil
import com.intellij.openapi.keymap.impl.ui.Group
import com.intellij.openapi.project.DumbAwareToggleAction
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.dialog
import com.intellij.ui.components.panels.Wrapper
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import com.intellij.util.ui.tree.TreeUtil
import org.jetbrains.annotations.Nls
import java.awt.Dimension
import java.util.function.Supplier
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

/**
 * A panel with an action tree and several filtering components.
 *
 * When only one group is passed, the tree looks like a list.
 * The panel has a filtering button to toggle the view between a filtered state
 * that shows only `groupIds` and a full view with a whole action tree
 * to choose any registered action in the platform.
 *
 * Use [ActionGroupPanel.showDialog] to show a user dialog with the panel.
 *
 * @param groupIds action group IDs to be shown when filter is on
 * @param showAllActionsFirst set unfiltered state by default
 * @param enableFilterAction hide filter toggle button from the panel
 * @param filterButtonText customize filter toggle button name
 */
class ActionGroupPanel(
  val groupIds: List<String>,
  val showAllActionsFirst: Boolean = false,
  var enableFilterAction: Boolean = true,
  var filterButtonText: Supplier<@Nls String?> = Presentation.NULL_STRING
) : BorderLayoutPanel() {

  constructor() : this(
    groupIds = emptyList(),
    showAllActionsFirst = true,
    enableFilterAction = false,
    filterButtonText = Supplier { null }
  )

  private val tree: Tree = Tree().apply {
    cellRenderer = CustomizableActionsPanel.createDefaultRenderer()
    isRootVisible = false
  }

  init {
    addToTop(createTopPanel())
    addToCenter(createCentralPanel())

    preferredSize = Dimension(420, 300)

    addActions(showAllActionsFirst)
  }

  /**
   * @return selected in the tree actions.
   */
  fun getSelectedActions(): List<Any> {
    val paths = tree.selectionPaths ?: return emptyList()
    return paths.asSequence().mapNotNull(TreeUtil::getLastUserObject).toList()
  }

  private fun addActions(allActionRequested: Boolean) {
    val treeModel = if (allActionRequested) {
      addAllActions()
    }
    else {
      addCustomActionGroups(groupIds)
    }
    tree.model = treeModel
    val rootNode = treeModel.root
    tree.showsRootHandles = rootNode !is DefaultMutableTreeNode || isRootNode(rootNode)
  }

  private fun addAllActions() : DefaultTreeModel {
    val rootGroup = ActionsTreeUtil.createMainGroup(null, null, getInstance().allQuickLists)
    val root = ActionsTreeUtil.createNode(rootGroup)
    return DefaultTreeModel(root)
  }

  private fun addCustomActionGroups(groupIDs: List<String>) : DefaultTreeModel {
    val currentSchema = CustomActionsSchema.getInstance()
    val treeNodes = getActions(groupIDs, currentSchema).map { ActionsTreeUtil.createNode(it) }
    if (groupIDs.size == 1) {
      return DefaultTreeModel(treeNodes.first())
    } else {
      val root = createRootNode()
      treeNodes.forEach(root::add)
      return DefaultTreeModel(root)
    }
  }

  @Suppress("HardCodedStringLiteral", "DialogTitleCapitalization")
  private fun createRootNode() : DefaultMutableTreeNode {
    val rootGroup = Group("root", null, null)
    return DefaultMutableTreeNode(rootGroup)
  }

  private fun isRootNode(node: DefaultMutableTreeNode?): Boolean {
    val rootGroup = TreeUtil.getUserObject(node) as? Group ?: return false
    val rootNamesAvailable = setOf("root", KeyMapBundle.message("all.actions.group.title"))
    return rootNamesAvailable.contains(rootGroup.name) && rootGroup.id == null && rootGroup.icon == null
  }

  private fun createCentralPanel() = Wrapper().apply {
    setContent(ScrollPaneFactory.createScrollPane(tree))
    border = JBUI.Borders.empty(3)
  }

  private fun createTopPanel() = BorderLayoutPanel().apply {
    if (enableFilterAction) {
      val actionGroup = DefaultActionGroup(*createActions())
      val toolbar = ActionManager.getInstance().createActionToolbar("ActionGroupPanel", actionGroup, true).apply {
        setReservePlaceAutoPopupIcon(false)
        layoutPolicy = ActionToolbar.NOWRAP_LAYOUT_POLICY
        targetComponent = tree
      }
      addToRight(toolbar.component)
    }
    addToCenter(CustomizableActionsPanel.setupFilterComponent(tree))
  }

  private fun createActions(): Array<AnAction> = arrayOf(
    object : DumbAwareToggleAction(Supplier { filterButtonText.get() }, Presentation.NULL_STRING, AllIcons.General.Filter) {
      private var isFiltered = !showAllActionsFirst

      override fun isSelected(e: AnActionEvent): Boolean = isFiltered

      override fun setSelected(e: AnActionEvent, state: Boolean) {
        isFiltered = state
        addActions(!isFiltered)
      }

      override fun update(e: AnActionEvent) {
        e.presentation.isVisible = enableFilterAction
        super.update(e)
      }
    }
  )

  companion object {
    /**
     * Shows dialog with the customization panel.
     *
     * @param title dialog title
     * @param groupIds groups to be shown first (see [ActionGroupPanel])
     * @param customizePanel block is called after panel is created
     */
    fun showDialog(@Nls title: String, vararg groupIds: String, customizePanel: ActionGroupPanel.() -> Unit = {}): List<Any>? {
      val panel = ActionGroupPanel(
        groupIds = listOf(*groupIds),
        showAllActionsFirst = groupIds.isEmpty(),
        enableFilterAction = groupIds.isNotEmpty()
      )
      panel.customizePanel()
      val dialog = dialog(
        title = title,
        panel = panel,
        resizable = true
      )
      return if (dialog.showAndGet()) panel.getSelectedActions().toList() else null
    }

    fun getActions(groupIDs: List<String>, currentSchema: CustomActionsSchema): Sequence<Group> {
      return groupIDs.asSequence()
        .map { it to currentSchema.getCorrectedAction(it) as? ActionGroup }
        .mapNotNull { (id, group) ->
          group ?: return@mapNotNull null
          @NlsSafe val name = currentSchema.getDisplayName(id)
          ActionsTreeUtil.createGroup(group, name, null, null, false) { true }
        }
    }
  }
}