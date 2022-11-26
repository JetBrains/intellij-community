// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.bookmark.ui

import com.intellij.ide.bookmark.ui.tree.GroupNode
import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.project.DumbAware
import com.intellij.util.ui.tree.TreeUtil
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode

internal class ContextMenuActionGroup(private val tree: JTree) : DumbAware, ActionGroup() {

  override fun getChildren(event: AnActionEvent?): Array<AnAction> {
    val paths = TreeUtil.getSelectedPathsIfAll(tree) { it.parentPath?.findFolderNode != null }
    val actions = mutableListOf<AnAction>()

    val addGroup: (String) -> Unit = {
      val group = (CustomActionsSchema.getInstance().getCorrectedAction(it) as? ActionGroup)
      if (group != null) actions.add(group)
    }

    if (paths != null) {
      // Sub-item of a folder bookmark
      val projectActions = (CustomActionsSchema.getInstance().getCorrectedAction("ProjectViewPopupMenu") as? ActionGroup)?.getChildren(event) ?: AnAction.EMPTY_ARRAY
      actions.addAll(projectActions)
    }
    else {
      // Bookmark item
      addGroup("Bookmarks.ToolWindow.PopupMenu")
      if ((TreeUtil.getSelectedPathIfOne(tree)?.lastPathComponent as? DefaultMutableTreeNode)?.userObject !is GroupNode) {
        // Not a bookmark group: add some project view context menu actions
        actions.add(Separator())
        addGroup("AnalyzeMenu")
        actions.add(Separator())
        addGroup("ProjectViewPopupMenuRefactoringGroup")
        actions.add(Separator())
        addGroup("ProjectViewPopupMenuRunGroup")
        actions.add(Separator())
        addGroup("VersionControlsGroup")
        actions.add(Separator())
        val selectAction = CustomActionsSchema.getInstance().getCorrectedAction("SelectInProjectView")
        if (selectAction != null) actions.add(selectAction)
      }
    }

    return actions.toTypedArray()
  }
}
