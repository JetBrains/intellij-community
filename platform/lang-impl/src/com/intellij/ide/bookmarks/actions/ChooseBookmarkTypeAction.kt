// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.bookmarks.actions

import com.intellij.ide.bookmarks.BookmarkBundle.message
import com.intellij.ide.bookmarks.BookmarkType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys.CONTEXT_COMPONENT
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.popup.PopupState
import org.jetbrains.annotations.Nls

internal class ChooseBookmarkTypeAction : DumbAwareAction() {
  private val popupState = PopupState.forPopup()

  override fun update(event: AnActionEvent) {
    val context = event.dataContext.context
    event.presentation.isVisible = context != null
    event.presentation.isEnabled = context != null && popupState.isHidden
    event.presentation.text = getActionText(context?.bookmark?.type)
  }

  override fun actionPerformed(event: AnActionEvent) {
    val context = event.dataContext.context ?: return
    val current = context.bookmark?.type
    val chooser = MnemonicChooser(context.manager, current) {
      popupState.hidePopup()
      if (current != it) context.setType(it)
    }
    val popup = JBPopupFactory.getInstance().createComponentPopupBuilder(chooser, chooser.buttons().first())
      .setTitle(getPopupTitle(current))
      .setFocusable(true)
      .setRequestFocus(true)
      .setMovable(false)
      .setResizable(false)
      .createPopup()

    popupState.prepareToShow(popup)
    context.getPointOnGutter(event.getData(CONTEXT_COMPONENT))?.let { popup.show(it) }
    ?: popup.showInBestPositionFor(event.dataContext)
  }

  @Nls
  private fun getActionText(type: BookmarkType?) = when (type) {
    null -> message("mnemonic.chooser.bookmark.create.action.text")
    BookmarkType.DEFAULT -> message("mnemonic.chooser.mnemonic.assign.action.text")
    else -> message("mnemonic.chooser.mnemonic.change.action.text")
  }

  @Nls
  private fun getPopupTitle(type: BookmarkType?) = when (type) {
    null -> message("mnemonic.chooser.bookmark.create.popup.title")
    BookmarkType.DEFAULT -> message("mnemonic.chooser.mnemonic.assign.popup.title")
    else -> message("mnemonic.chooser.mnemonic.change.popup.title")
  }

  init {
    isEnabledInModalContext = true
  }
}
