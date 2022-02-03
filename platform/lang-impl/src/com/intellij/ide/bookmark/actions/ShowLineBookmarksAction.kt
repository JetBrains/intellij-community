// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.bookmark.actions

import com.intellij.ide.bookmark.BookmarkBundle
import com.intellij.ide.bookmark.ui.BookmarksView
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Disposer
import com.intellij.ui.popup.PopupState
import com.intellij.util.ui.JBUI

internal class ShowLineBookmarksAction : DumbAwareAction(BookmarkBundle.messagePointer("show.bookmarks.action.text")) {
  private val popupState = PopupState.forPopup()

  override fun update(event: AnActionEvent) {
    event.presentation.isEnabled = event.project != null
    event.presentation.text = when (AdvancedSettings.getBoolean("show.line.bookmarks.in.popup")) {
      true -> BookmarkBundle.message("show.line.bookmarks.action.text")
      else -> BookmarkBundle.message("show.bookmarks.action.text")
    }
  }

  override fun actionPerformed(event: AnActionEvent) {
    if (popupState.isRecentlyHidden) return
    if (popupState.isShowing) return popupState.hidePopup()

    val project = event.project ?: return
    val panel = BookmarksView(project, null)
    panel.preferredSize = JBUI.size(640, 240)

    event.bookmarksManager?.assignedTypes?.forEach { panel.registerBookmarkTypeAction(panel, it) }
    panel.registerEditSourceAction(panel)
    panel.tree.registerNavigateOnEnterAction()

    val popup = JBPopupFactory.getInstance()
      .createComponentPopupBuilder(panel, panel.tree)
      .setDimensionServiceKey(project, "ShowBookmarks", false)
      .setTitle(BookmarkBundle.message("popup.title.bookmarks"))
      .setFocusable(true)
      .setRequestFocus(true)
      .setMovable(true)
      .setResizable(true)
      .setCancelOnOtherWindowOpen(true)
      .createPopup()

    Disposer.register(popup, panel)
    popupState.prepareToShow(popup)
    popup.showCenteredInCurrentWindow(project)
  }
}
