// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.bookmark.actions

import com.intellij.ide.DeleteProvider
import com.intellij.ide.bookmark.ui.BookmarksView
import com.intellij.ide.bookmark.ui.BookmarksViewState
import com.intellij.ide.bookmark.ui.tree.BookmarkNode
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.PlatformDataKeys

class BookmarksDeleteProvider: DeleteProvider {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun deleteElement(dataContext: DataContext) {
    val items = dataContext.getData(PlatformDataKeys.SELECTED_ITEMS) ?: return
    val project = dataContext.getData(PlatformDataKeys.PROJECT) ?: return
    val view = dataContext.getData(BookmarksView.BOOKMARKS_VIEW) ?: return

    @Suppress("UNCHECKED_CAST")
    val nodes = items.asList() as? List<AbstractTreeNode<*>> ?: return
    val state = BookmarksViewState.getInstance(project)
    NodeDeleteAction.deleteSelectedNodes(state, nodes, project, view)
  }

  override fun canDeleteElement(dataContext: DataContext): Boolean {
    val items = dataContext.getData(PlatformDataKeys.SELECTED_ITEMS) ?: return false
    return items.all { it is BookmarkNode<*> }
  }
}