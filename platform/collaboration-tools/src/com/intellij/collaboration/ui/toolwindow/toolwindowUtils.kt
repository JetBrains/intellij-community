// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.toolwindow

import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ex.ToolWindowEx
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentManager
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.ui.content.ContentManagerListener
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
fun ToolWindow.dontHideOnEmptyContent() {
  setToHideOnEmptyContent(false)
  (this as? ToolWindowEx)?.emptyText?.text = ""
}

// TODO: should be removed when GitHub toolwindow tabs moved to common logic
@ApiStatus.Internal
fun ToolWindow.refreshReviewListOnSelection(
  onSelection: (Content) -> Unit
) {
  refreshReviewListOnTabSelection(contentManager, onSelection)
  refreshListOnToolwindowShow(this, onSelection)
}

private fun refreshReviewListOnTabSelection(contentManager: ContentManager, onSelection: (Content) -> Unit) {
  contentManager.addContentManagerListener(object : ContentManagerListener {
    override fun selectionChanged(event: ContentManagerEvent) {
      if (event.operation == ContentManagerEvent.ContentOperation.add) {
        // tab selected
        onSelection(event.content)
      }
    }
  })
}

private fun refreshListOnToolwindowShow(toolwindow: ToolWindow, onSelection: (Content) -> Unit) {
  val bus = toolwindow.project.messageBus.connect(toolwindow.contentManager)
  bus.subscribe(ToolWindowManagerListener.TOPIC, object : ToolWindowManagerListener {
    override fun toolWindowShown(shownToolwindow: ToolWindow) {
      if (shownToolwindow.id == toolwindow.id) {
        val selectedContent = shownToolwindow.contentManager.selectedContent
        if (selectedContent != null) {
          onSelection(selectedContent)
        }
      }
    }
  })
}