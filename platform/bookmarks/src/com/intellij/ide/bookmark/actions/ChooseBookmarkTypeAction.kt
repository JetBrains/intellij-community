// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.bookmark.actions

import com.intellij.ide.bookmark.BookmarkBundle
import com.intellij.ide.bookmark.BookmarkType
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.popup.JBPopupFactory

internal class ChooseBookmarkTypeAction : DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(event: AnActionEvent) {
    if (checkMultipleSelectionAndDisableAction(event)) return
    val manager = event.bookmarksManager
    val bookmark = event.contextBookmark
    val type = bookmark?.let { manager?.getType(it) }
    event.presentation.apply {
      isVisible = bookmark != null
      isEnabled = bookmark != null
      text = when (type) {
        BookmarkType.DEFAULT -> BookmarkBundle.message("mnemonic.chooser.mnemonic.assign.action.text")
        null -> BookmarkBundle.message("mnemonic.chooser.bookmark.create.action.text")
        else -> ActionsBundle.message("action.BookmarksView.ChooseType.text")
      }
    }
  }

  override fun actionPerformed(event: AnActionEvent) {
    val manager = event.bookmarksManager ?: return
    val bookmark = event.contextBookmark ?: return
    val type = manager.getType(bookmark)
    val chooser = BookmarkTypeChooser(type, manager.assignedTypes, bookmark.firstGroupWithDescription?.getDescription(bookmark))
    val title = when (type) {
      BookmarkType.DEFAULT -> BookmarkBundle.message("mnemonic.chooser.mnemonic.assign.popup.title")
      null -> BookmarkBundle.message("mnemonic.chooser.bookmark.create.popup.title")
      else -> BookmarkBundle.message("mnemonic.chooser.mnemonic.change.popup.title")
    }
    val popup = JBPopupFactory.getInstance().createComponentPopupBuilder(chooser.content, chooser.firstButton)
      .setFocusable(true).setRequestFocus(true)
      .setMovable(false).setResizable(false)
      .setTitle(title).createPopup()
    chooser.onChosen = { chosenType, description ->
      popup.closeOk(null)
      if (manager.getType(bookmark) == null) {
        manager.toggle(bookmark, chosenType)
      } else {
        manager.setType(bookmark, chosenType)
      }
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
