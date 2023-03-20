// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.bookmark.actions

import com.intellij.ide.bookmark.Bookmark
import com.intellij.ide.bookmark.BookmarkBundle
import com.intellij.ide.bookmark.BookmarkType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.popup.PopupState

internal class NodeChooseTypeAction : DumbAwareAction(BookmarkBundle.messagePointer("mnemonic.chooser.mnemonic.change.action.text")) {
  private val popupState = PopupState.forPopup()

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun update(event: AnActionEvent) {
    event.presentation.isEnabledAndVisible = false
    if (popupState.isShowing) return
    val manager = event.bookmarksManager ?: return
    val bookmark = event.bookmarkNodes?.singleOrNull()?.value as? Bookmark ?: return
    val type = manager.getType(bookmark) ?: return
    if (type == BookmarkType.DEFAULT) event.presentation.text = BookmarkBundle.message("mnemonic.chooser.mnemonic.assign.action.text")
    event.presentation.isEnabledAndVisible = true
  }

  override fun actionPerformed(event: AnActionEvent) {
    if (popupState.isRecentlyHidden) return
    val manager = event.bookmarksManager ?: return
    val bookmark = event.bookmarkNodes?.singleOrNull()?.value as? Bookmark ?: return
    val type = manager.getType(bookmark) ?: return
    val chooser = BookmarkTypeChooser(type, manager.assignedTypes, bookmark.firstGroupWithDescription?.getDescription(bookmark)) { chosenType, description ->
      popupState.hidePopup()
      manager.setType(bookmark, chosenType)
      if (description != "") {
        manager.getGroups(bookmark).firstOrNull()?.setDescription(bookmark, description)
      }
    }
    val title = when (type) {
      BookmarkType.DEFAULT -> BookmarkBundle.message("mnemonic.chooser.mnemonic.assign.popup.title")
      else -> BookmarkBundle.message("mnemonic.chooser.mnemonic.change.popup.title")
    }
    JBPopupFactory.getInstance().createComponentPopupBuilder(chooser.content, chooser.firstButton)
      .setFocusable(true).setRequestFocus(true)
      .setMovable(false).setResizable(false)
      .setTitle(title).createPopup()
      .also { popupState.prepareToShow(it) }
      .showInBestPositionFor(event.dataContext)
  }

  init {
    isEnabledInModalContext = true
  }
}
