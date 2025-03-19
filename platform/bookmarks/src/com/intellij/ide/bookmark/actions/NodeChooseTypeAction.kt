// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.bookmark.actions

import com.intellij.ide.bookmark.Bookmark
import com.intellij.ide.bookmark.BookmarkBundle
import com.intellij.ide.bookmark.BookmarkType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.popup.JBPopupFactory

internal class NodeChooseTypeAction : DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun update(event: AnActionEvent) {
    event.presentation.isEnabledAndVisible = false
    val manager = event.bookmarksManager ?: return
    val bookmark = event.bookmarkNodes?.singleOrNull()?.value as? Bookmark ?: return
    val type = manager.getType(bookmark) ?: return
    if (type == BookmarkType.DEFAULT) event.presentation.text = BookmarkBundle.message("mnemonic.chooser.mnemonic.assign.action.text")
    event.presentation.isEnabledAndVisible = true
  }

  override fun actionPerformed(event: AnActionEvent) {
    val manager = event.bookmarksManager ?: return
    val bookmark = event.bookmarkNodes?.singleOrNull()?.value as? Bookmark ?: return
    val type = manager.getType(bookmark) ?: return
    val chooser = BookmarkTypeChooser(type, manager.assignedTypes, bookmark.firstGroupWithDescription?.getDescription(bookmark))
    val title = when (type) {
      BookmarkType.DEFAULT -> BookmarkBundle.message("mnemonic.chooser.mnemonic.assign.popup.title")
      else -> BookmarkBundle.message("mnemonic.chooser.mnemonic.change.popup.title")
    }
    val popup = JBPopupFactory.getInstance().createComponentPopupBuilder(chooser.content, chooser.firstButton)
      .setFocusable(true).setRequestFocus(true)
      .setMovable(false).setResizable(false)
      .setTitle(title).createPopup()
    chooser.onChosen = { chosenType, description ->
      popup.closeOk(null)
      manager.setType(bookmark, chosenType)
      if (description != "") {
        manager.getGroups(bookmark).firstOrNull()?.setDescription(bookmark, description)
      }
    }
    popup.showInBestPositionFor(event.dataContext)
  }

  init {
    isEnabledInModalContext = true
  }
}
