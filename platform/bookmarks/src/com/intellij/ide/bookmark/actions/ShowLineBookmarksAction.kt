// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.bookmark.actions

import com.intellij.ide.bookmark.BookmarkBundle
import com.intellij.ide.bookmark.ui.BookmarksView
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Disposer
import com.intellij.ui.popup.AbstractPopup
import com.intellij.util.ui.JBUI

internal class ShowLineBookmarksAction : DumbAwareAction() {
  override fun update(event: AnActionEvent) {
    event.presentation.isEnabled = event.project != null
    event.presentation.text = when (AdvancedSettings.getBoolean("show.line.bookmarks.in.popup")) {
      true -> BookmarkBundle.message("show.line.bookmarks.action.text")
      else -> ActionsBundle.message("action.ShowBookmarks.text")
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun actionPerformed(event: AnActionEvent) {
    val project = event.project ?: return
    val panel = BookmarksView(project, null)
    panel.preferredSize = JBUI.DialogSizes.large()

    val popup = JBPopupFactory.getInstance()
      .createComponentPopupBuilder(panel, panel.tree)
      .setDimensionServiceKey(project, "ShowBookmarks", false)
      .setTitle(BookmarkBundle.message("popup.title.bookmarks"))
      .setFocusable(true)
      .setRequestFocus(true)
      .setMovable(true)
      .setResizable(true)
      .setNormalWindowLevel(true)
      .createPopup()
    popup.content.putClientProperty(AbstractPopup.FIRST_TIME_SIZE, JBUI.DialogSizes.large())

    event.bookmarksManager?.assignedTypes?.forEach { panel.registerBookmarkTypeAction(panel, it) { popup.closeOk(null) } }

    panel.addEditSourceListener { popup.closeOk(null) }

    Disposer.register(popup, panel)
    popup.showCenteredInCurrentWindow(project)
  }
}
