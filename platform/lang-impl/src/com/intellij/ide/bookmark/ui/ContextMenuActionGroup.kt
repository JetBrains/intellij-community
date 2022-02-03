// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.bookmark.ui

import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.impl.PopupMenuPreloader
import com.intellij.openapi.project.DumbAware
import com.intellij.ui.PopupHandler
import com.intellij.util.ui.tree.TreeUtil
import javax.swing.JTree

@Suppress("ComponentNotRegistered")
internal class ContextMenuActionGroup(private val tree: JTree) : DumbAware, ActionGroup() {

  override fun getChildren(event: AnActionEvent?): Array<AnAction> {
    val paths = TreeUtil.getSelectedPathsIfAll(tree) { it.parentPath?.findFolderNode != null }
    val id = if (paths != null) "ProjectViewPopupMenu" else "Bookmarks.ToolWindow.PopupMenu"
    val group = CustomActionsSchema.getInstance().getCorrectedAction(id) as? ActionGroup
    return group?.getChildren(event) ?: EMPTY_ARRAY
  }

  init {
    val place = "popup@BookmarksView"
    val handler = PopupHandler.installPopupMenu(tree, this, place)
    PopupMenuPreloader.install(tree, place, handler) { this }
  }
}
