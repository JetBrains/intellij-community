// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.bookmark.ui

import com.intellij.ide.actions.ToggleToolbarAction.isToolbarVisible
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Splittable
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowEx
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import java.util.concurrent.atomic.AtomicBoolean

private class BookmarksViewFactory : DumbAware, ToolWindowFactory, ToolWindowManagerListener {
  private val orientation = AtomicBoolean(true)

  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    val manager = toolWindow.contentManager
    val panel = BookmarksView(project, isToolbarVisible(toolWindow, project)).also { it.orientation = orientation.get() }
    manager.addContent(manager.factory.createContent(panel, null, false).apply { isCloseable = false })
    project.messageBus.connect(manager).subscribe(ToolWindowManagerListener.TOPIC, this)
    toolWindow.helpId = "bookmarks.tool.window.help"
    toolWindow.setTitleActions(listOfNotNull(ActionUtil.getAction("Bookmarks.ToolWindow.TitleActions")))
    if (toolWindow is ToolWindowEx) {
      toolWindow.setAdditionalGearActions(ActionUtil.getActionGroup("Bookmarks.ToolWindow.GearActions"))
    }
  }

  override fun stateChanged(manager: ToolWindowManager) {
    val window = manager.getToolWindow(ToolWindowId.BOOKMARKS) ?: return
    if (window.isDisposed) return
    val vertical = !window.anchor.isHorizontal
    if (vertical != orientation.getAndSet(vertical)) {
      for (content in window.contentManager.contents) {
        val splittable = content?.component as? Splittable
        splittable?.orientation = vertical
      }
    }
  }
}
