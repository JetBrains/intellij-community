// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.bookmark.actions

import com.intellij.ide.bookmark.BookmarkBundle
import com.intellij.ide.bookmark.BookmarkType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.popup.PopupState

internal class ChooseBookmarkTypeAction : DumbAwareAction(BookmarkBundle.messagePointer("mnemonic.chooser.mnemonic.toggle.action.text")) {
  private val popupState = PopupState.forPopup()

  override fun update(event: AnActionEvent) {
    val manager = event.bookmarksManager
    val bookmark = event.contextBookmark
    val type = bookmark?.let { manager?.getType(it) }
    event.presentation.apply {
      isVisible = bookmark != null
      isEnabled = bookmark != null && popupState.isHidden
      text = when (type) {
        BookmarkType.DEFAULT -> BookmarkBundle.message("mnemonic.chooser.mnemonic.assign.action.text")
        null -> BookmarkBundle.message("mnemonic.chooser.bookmark.create.action.text")
        else -> BookmarkBundle.message("mnemonic.chooser.mnemonic.change.action.text")
      }
    }
  }

  override fun actionPerformed(event: AnActionEvent) {
    if (popupState.isRecentlyHidden) return
    val manager = event.bookmarksManager ?: return
    val bookmark = event.contextBookmark ?: return
    val type = manager.getType(bookmark)
    val chooser = BookmarkTypeChooser(type, manager.assignedTypes) {
      popupState.hidePopup()
      if (it != type) manager.toggle(bookmark, it)
    }
    val title = when (type) {
      BookmarkType.DEFAULT -> BookmarkBundle.message("mnemonic.chooser.mnemonic.assign.popup.title")
      null -> BookmarkBundle.message("mnemonic.chooser.bookmark.create.popup.title")
      else -> BookmarkBundle.message("mnemonic.chooser.mnemonic.change.popup.title")
    }
    JBPopupFactory.getInstance().createComponentPopupBuilder(chooser, chooser.buttons().first())
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
