// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.collaboration.ui.codereview.changes

import com.intellij.collaboration.ui.SingleValueModel
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ui.*
import com.intellij.ui.ClientProperty
import com.intellij.ui.ExpandableItemsHandler
import com.intellij.ui.SelectionSaver
import com.intellij.util.ui.tree.TreeUtil
import org.jetbrains.annotations.ApiStatus.Obsolete
import org.jetbrains.annotations.Nls
import javax.swing.JComponent
import javax.swing.tree.DefaultTreeModel

@Obsolete
class CodeReviewChangesTreeFactory(private val project: Project,
                                   private val changesModel: SingleValueModel<out Collection<Change>>) {

  fun create(emptyTextText: @Nls String, preselectFirstChange: Boolean = true): AsyncChangesTree {
    val tree = object : AsyncChangesTree(project, false, false) {
      override val changesTreeModel: AsyncChangesTreeModel = SimpleAsyncChangesTreeModel.create { grouping ->
        TreeModelBuilder.buildFromChanges(project, grouping, changesModel.value, null)
      }

      override fun updateTreeModel(model: DefaultTreeModel, treeStateStrategy: TreeStateStrategy<*>) {
        super.updateTreeModel(model, treeStateStrategy)

        if (preselectFirstChange && isSelectionEmpty && !isEmpty) {
          TreeUtil.selectFirstNode(this)
        }
      }

      override fun uiDataSnapshot(sink: DataSink) {
        super.uiDataSnapshot(sink)
        VcsTreeModelData.uiDataSnapshot(sink, project, this)
      }

      override fun getToggleClickCount(): Int {
        return toggleClickCount
      }

    }.apply {
      emptyText.text = emptyTextText
      toggleClickCount = 2
    }.also {
      ClientProperty.put(it, ExpandableItemsHandler.IGNORE_ITEM_SELECTION, true)
      SelectionSaver.installOn(it)
    }
    changesModel.addAndInvokeListener { tree.rebuildTree() }
    return tree
  }

  companion object {
    fun createTreeToolbar(actionManager: ActionManager, groupName: String, treeContainer: JComponent): JComponent {
      val changesToolbarActionGroup = actionManager.getAction(groupName) as ActionGroup
      val changesToolbar = actionManager.createActionToolbar("ChangesBrowser", changesToolbarActionGroup, true)
      val treeActionsGroup = DefaultActionGroup(actionManager.getAction(IdeActions.ACTION_EXPAND_ALL),
                                                actionManager.getAction(IdeActions.ACTION_COLLAPSE_ALL))
      return TreeActionsToolbarPanel(changesToolbar, treeActionsGroup, treeContainer)
    }
  }
}