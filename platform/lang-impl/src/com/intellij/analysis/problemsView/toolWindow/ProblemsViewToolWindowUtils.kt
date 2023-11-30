package com.intellij.analysis.problemsView.toolWindow

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

object ProblemsViewToolWindowUtils {
  fun selectContent(contentManager: ContentManager, id: String) {
    val content = contentManager.contents.filter {
      val problemsView = it.component as? ProblemsViewTab
      return@filter (problemsView != null && problemsView.getTabId() == id)
    }.firstOrNull()

    if (content != null) contentManager.setSelectedContent(content)
  }

  fun getToolWindow(project: Project): ToolWindow? = ToolWindowManager.getInstance(project).getToolWindow(ProblemsView.ID)

  fun getTabById(project: Project, id: String): ProblemsViewTab? = getContentById(project, id)?.component as? ProblemsViewTab

  fun getSelectedTab(project: Project): ProblemsViewTab? {
    return getToolWindow(project)?.contentManager?.selectedContent?.component as? ProblemsViewTab
  }

  fun addTab(project: Project, provider: ProblemsViewPanelProvider) {
    ProblemsView.addPanel(project, provider)
  }

  fun removeTab(project: Project, id: String) {
    ProblemsView.removePanel(project, id)
  }

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

  suspend fun selectTabAsync(project: Project, id: String) {
    val toolWindow = getToolWindow(project) ?: return

    withContext(Dispatchers.EDT) {
      val content = getContentById(project, id) ?: return@withContext

      if (!toolWindow.isVisible) {
        suspendCoroutine { continuation ->
          toolWindow.show {
            toolWindow.contentManager.setSelectedContent(content, true)
            continuation.resume(Unit)
          }
        }
      } else {
        toolWindow.contentManager.setSelectedContent(content, true)
      }
    }
  }

  @JvmStatic
  fun selectProjectErrorsTab(project: Project) {
    selectTab(project, ProblemsViewProjectErrorsPanelProvider.ID)
  }
  
  fun getContentById(project: Project, id: String): Content? {
    val toolWindow = getToolWindow(project) ?: return null
    val content = toolWindow.contentManager.contents.filter {
      val problemsView = it.component as? ProblemsViewTab
      return@filter (problemsView != null && problemsView.getTabId() == id)
    }.firstOrNull()
    return content
  }
}