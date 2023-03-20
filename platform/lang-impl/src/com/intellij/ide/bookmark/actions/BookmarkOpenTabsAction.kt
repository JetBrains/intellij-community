// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.bookmark.actions

import com.intellij.ide.bookmark.BookmarkBundle
import com.intellij.ide.bookmark.BookmarkType
import com.intellij.ide.bookmark.ui.GroupCreateDialog
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.vfs.VirtualFile


class BookmarkOpenTabsAction: DumbAwareAction(BookmarkBundle.message("bookmark.open.tabs.text")) {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(event: AnActionEvent) {
    val project = event.project
    if (project == null) {
      event.presentation.isEnabledAndVisible = false
      return
    }
    event.presentation.isVisible = true
    event.presentation.isEnabled = false
    for (editor in EditorFactory.getInstance().allEditors) {
      if (editor.project != project) continue
      event.presentation.isEnabledAndVisible = true
      break
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val manager = e.bookmarksManager ?: return
    val files = mutableListOf<VirtualFile>()
    val fileDocManager = FileDocumentManager.getInstance()
    for (editor in EditorFactory.getInstance().allEditors) {
      if (editor.project != project) continue
      val file = fileDocManager.getFile(editor.document) ?: continue
      files.add(file)
    }

    val group = GroupCreateDialog(project, null, manager)
      .showAndGetGroup(false)
                ?: return

    files
      .mapNotNull { manager.createBookmark(it) }
      .forEach { group.add(it, BookmarkType.DEFAULT) }
  }
}