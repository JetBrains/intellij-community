// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.bookmark.actions

import com.intellij.ide.bookmark.BookmarksManagerImpl
import com.intellij.ide.bookmark.ui.tree.GroupNode
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction

internal class SortGroupBookmarksAction : DumbAwareAction() {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(event: AnActionEvent) {
    val manager = event.bookmarksManager as? BookmarksManagerImpl
    event.presentation.isEnabledAndVisible = manager != null &&
                                             getSelectedGroupNodes(event).firstOrNull() != null
  }

  override fun actionPerformed(event: AnActionEvent) {
    val manager = event.bookmarksManager as? BookmarksManagerImpl ?: return
    val nodes = getSelectedGroupNodes(event)
    for (groupNode in nodes) {
      manager.sort(groupNode.value)
    }
  }

  private fun getSelectedGroupNodes(event: AnActionEvent): Sequence<GroupNode> {
    val nodes = event.bookmarkNodes ?: return emptySequence()
    return nodes.asSequence().flatMap { node -> generateSequence(node) { it.parent } }
      .filterIsInstance<GroupNode>()
      .distinct()
  }
}
