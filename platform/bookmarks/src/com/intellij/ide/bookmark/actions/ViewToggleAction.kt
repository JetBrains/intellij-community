// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.bookmark.actions

import com.intellij.ide.bookmark.ui.BookmarksView
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.ToggleOptionAction
import com.intellij.openapi.project.DumbAware

internal open class ViewToggleAction(option: (BookmarksView?) -> Option?)
  : DumbAware, ToggleOptionAction({ option(it.bookmarksView) }) {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

internal class AutoscrollFromSourceToggleAction : ViewToggleAction({ it?.autoScrollFromSource })
internal class AutoscrollToSourceToggleAction : ViewToggleAction({ it?.autoScrollToSource })
internal class GroupLineBookmarksToggleAction : ViewToggleAction({ it?.groupLineBookmarks })
internal class OpenInPreviewTabToggleAction : ViewToggleAction({ it?.openInPreviewTab })
internal class ShowPreviewToggleAction : ViewToggleAction({ it?.showPreview })
