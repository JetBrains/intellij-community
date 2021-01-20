// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.bookmarks.actions

import com.intellij.ide.bookmarks.BookmarkBundle
import com.intellij.ide.bookmarks.BookmarkType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys.CONTEXT_COMPONENT
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent

internal class ChooseBookmarkTypeAction : DumbAwareAction() {
  private var isPopupShown = false

  override fun update(event: AnActionEvent) {
    val context = event.dataContext.context
    event.presentation.isEnabled = !isPopupShown && context != null
    event.presentation.text = BookmarkBundle.message("action.presentation.ToggleBookmarkWithMnemonicAction.text")
  }

  override fun actionPerformed(event: AnActionEvent) {
    val context = event.dataContext.context ?: return
    val bookmark = context.bookmark
    val current = BookmarkType.get(bookmark?.mnemonic ?: Char.MIN_VALUE)

    object : MnemonicChooser(context.manager, current) {
      override fun onChosen(type: BookmarkType) {
        super.onChosen(type)
        if (current != type) context.setType(type)
      }
    }.createPopup(true).apply {
      addListener(object : JBPopupListener {
        override fun beforeShown(event: LightweightWindowEvent) {
          isPopupShown = true
        }

        override fun onClosed(event: LightweightWindowEvent) {
          isPopupShown = false
        }
      })
      context.getPointOnGutter(event.getData(CONTEXT_COMPONENT))?.let { show(it) }
      ?: showInBestPositionFor(event.dataContext)
    }
  }

  init {
    isEnabledInModalContext = true
  }
}
