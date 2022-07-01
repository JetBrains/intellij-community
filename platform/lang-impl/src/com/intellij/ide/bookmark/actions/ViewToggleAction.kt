// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.bookmark.actions

import com.intellij.ide.bookmark.BookmarkBundle.messagePointer
import com.intellij.ide.bookmark.ui.BookmarksView
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.ToggleOptionAction
import com.intellij.openapi.project.DumbAware

internal open class ViewToggleAction(key: String, option: (BookmarksView?) -> Option?)
  : DumbAware, ToggleOptionAction({ option(it.bookmarksView) }) {
  init {
    templatePresentation.setText(messagePointer(key))
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

internal class AutoscrollFromSourceToggleAction : ViewToggleAction("view.autoscroll.from.source.action.text", { it?.autoScrollFromSource })
internal class AutoscrollToSourceToggleAction : ViewToggleAction("view.autoscroll.to.source.action.text", { it?.autoScrollToSource })
internal class GroupLineBookmarksToggleAction : ViewToggleAction("view.group.line.bookmarks.action.text", { it?.groupLineBookmarks })
internal class OpenInPreviewTabToggleAction : ViewToggleAction("view.open.in.preview.tab.action.text", { it?.openInPreviewTab })
internal class ShowPreviewToggleAction : ViewToggleAction("view.show.preview.action.text", { it?.showPreview })
