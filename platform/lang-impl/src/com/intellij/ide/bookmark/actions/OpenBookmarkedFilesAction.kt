// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.bookmark.actions

import com.intellij.ide.bookmark.BookmarkBundle
import com.intellij.ide.bookmark.FileBookmark
import com.intellij.ide.bookmark.ui.tree.GroupNode
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAwareAction

class OpenBookmarkedFilesAction : DumbAwareAction(BookmarkBundle.messagePointer("open.all.bookmarked.files")) {

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = e.bookmarkNodes?.all { it is GroupNode } ?: false
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val fileEditorManager = FileEditorManager.getInstance(project)
    e.bookmarkNodes?.forEach { group ->
      if (group is GroupNode) {
        for (it in group.children) {
          val fileBookmark = it.value as? FileBookmark ?: continue
          val file = fileBookmark.file
          fileEditorManager.openFile(file, true)
        }
      }
    }
  }
}