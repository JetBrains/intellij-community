package com.intellij.analysis.problemsView.toolWindow

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentManager

object ProblemsViewToolWindowUtils {
  fun selectContent(contentManager: ContentManager, id: String) {
    val content = contentManager.contents.filter {
      val problemsView = it.component as? ProblemsViewTab
      return@filter (problemsView != null && problemsView.getTabId() == id)
    }.firstOrNull()

    if (content != null) contentManager.setSelectedContent(content)
  }

  fun getToolWindow(project: Project) = ToolWindowManager.getInstance(project).getToolWindow(ProblemsView.ID)

  fun getTabById(project: Project, id: String): ProblemsViewTab? = getContentById(project, id)?.component as? ProblemsViewTab

  fun selectTab(project: Project, id: String) {
    val toolWindow = getToolWindow(project) ?: return

    ApplicationManager.getApplication().invokeLater {
      val content = getContentById(project, id) ?: return@invokeLater

      if (!toolWindow.isVisible) toolWindow.show {
        toolWindow.contentManager.setSelectedContent(content, true)
      } else {
        toolWindow.contentManager.setSelectedContent(content, true)
      }
    }
  }

  private fun getContentById(project: Project, id: String): Content? {
    val toolWindow = getToolWindow(project) ?: return null
    val content = toolWindow.contentManager.contents.filter {
      val problemsView = it.component as? ProblemsViewTab
      return@filter (problemsView != null && problemsView.getTabId() == id)
    }.firstOrNull()
    return content
  }
}