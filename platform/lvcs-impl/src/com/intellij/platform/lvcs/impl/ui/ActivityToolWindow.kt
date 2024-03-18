// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lvcs.impl.ui

import com.intellij.history.integration.LocalHistoryBundle
import com.intellij.icons.AllIcons
import com.intellij.icons.ExpUiIcons
import com.intellij.ide.impl.ContentManagerWatcher
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.RegisterToolWindowTask
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.ui.content.ContentManagerListener

object ActivityToolWindow {
  private const val TOOLWINDOW_ID: String = "Activity" // NON-NLS

  @JvmStatic
  fun showTab(project: Project, content: Content) {
    content.putUserData(Content.SIMPLIFIED_TAB_RENDERING_KEY, true)

    val toolWindow = getToolWindow(project)
    toolWindow.contentManager.addContent(content)
    toolWindow.activate { toolWindow.contentManager.setSelectedContent(content, true) }
  }

  fun showTab(project: Project, condition: (Content) -> Boolean): Boolean {
    val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOLWINDOW_ID) ?: return false
    val content = toolWindow.contentManager.contents.firstOrNull(condition) ?: return false
    toolWindow.activate { toolWindow.contentManager.setSelectedContent(content, true) }
    return true
  }

  private fun getToolWindow(project: Project): ToolWindow {
    val toolWindowManager = ToolWindowManager.getInstance(project)
    return toolWindowManager.getToolWindow(TOOLWINDOW_ID) ?: registerToolWindow(toolWindowManager)
  }

  private fun registerToolWindow(toolWindowManager: ToolWindowManager): ToolWindow {
    val toolWindow = toolWindowManager.registerToolWindow(RegisterToolWindowTask(
      id = TOOLWINDOW_ID,
      anchor = ToolWindowAnchor.LEFT,
      canCloseContent = true,
      stripeTitle = LocalHistoryBundle.messagePointer("activity.toolwindow.title"),
      icon = if (ExperimentalUI.isNewUI()) ExpUiIcons.General.History else AllIcons.Vcs.History
    ))
    ContentManagerWatcher.watchContentManager(toolWindow, toolWindow.contentManager)
    return toolWindow
  }

  internal fun onContentVisibilityChanged(project: Project, content: Content, disposable: Disposable, onVisibilityChanged: (Boolean) -> Unit) {
    val activityToolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOLWINDOW_ID) ?: return
    val isVisiblePredicate = { activityToolWindow.isVisible && activityToolWindow.contentManagerIfCreated?.selectedContent == content }

    val contentManagerListener = object : ContentManagerListener {
      override fun selectionChanged(event: ContentManagerEvent) {
        onVisibilityChanged(isVisiblePredicate())
      }
    }
    activityToolWindow.addContentManagerListener(contentManagerListener)
    Disposer.register(disposable, Disposable {
      activityToolWindow.contentManagerIfCreated?.removeContentManagerListener(contentManagerListener)
    })

    project.messageBus.connect(disposable).subscribe(ToolWindowManagerListener.TOPIC, object : ToolWindowManagerListener {
      override fun stateChanged(toolWindowManager: ToolWindowManager,
                                toolWindow: ToolWindow,
                                changeType: ToolWindowManagerListener.ToolWindowManagerEventType) {
        if (toolWindow == activityToolWindow) onVisibilityChanged(isVisiblePredicate())
      }
    })

    onVisibilityChanged(isVisiblePredicate())
  }
}