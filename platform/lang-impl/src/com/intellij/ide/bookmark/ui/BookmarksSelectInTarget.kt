// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.bookmark.ui

import com.intellij.ide.SelectInContext
import com.intellij.ide.SelectInTarget
import com.intellij.ide.StandardTargetWeights.BOOKMARKS_WEIGHT
import com.intellij.ide.bookmark.BookmarkBundle.message
import com.intellij.ide.bookmark.BookmarksManager
import com.intellij.ide.bookmark.FileBookmark
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore.isAncestor
import com.intellij.openapi.wm.ToolWindowId.BOOKMARKS
import com.intellij.openapi.wm.ToolWindowManager

internal class BookmarksSelectInTarget(val project: Project) : SelectInTarget {
  override fun toString() = message("select.in.target.name")
  override fun getToolWindowId() = BOOKMARKS
  override fun getWeight() = BOOKMARKS_WEIGHT

  override fun canSelect(context: SelectInContext?): Boolean {
    val file = context?.virtualFile ?: return false
    val manager = BookmarksManager.getInstance(project) ?: return false
    return manager.bookmarks.any {
      if (it is FileBookmark) {
        val ancestor = it.file
        if (ancestor.isDirectory) {
          isAncestor(ancestor, file, false)
        }
        else {
          ancestor == file
        }
      }
      else false
    }
  }

  override fun selectIn(context: SelectInContext, requestFocus: Boolean) {
    val window = ToolWindowManager.getInstance(project).getToolWindow(BOOKMARKS) ?: return
    val view = window.contentManager.selectedContent?.component as? BookmarksView ?: return
    when (requestFocus) {
      true -> window.activate { selectIn(context, view) }
      else -> window.show { selectIn(context, view) }
    }
  }

  private fun selectIn(context: SelectInContext, view: BookmarksView) {
    // TODO: navigate to the bookmark if possible
    view.select(context.virtualFile)
  }
}
