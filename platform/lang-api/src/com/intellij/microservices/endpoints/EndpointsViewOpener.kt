package com.intellij.microservices.endpoints

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager.Companion.getInstance
import com.intellij.util.messages.Topic

interface EndpointsViewOpener {
  fun showEndpoints(filter: String?)

  fun showEndpoints(module: String?, framework: String?, filter: String?)

  companion object {
    const val ENDPOINTS_TOOLWINDOW_ID: String = "Endpoints"
    const val ENDPOINTS_CONTEXT_MENU_PLACE: String = "popup@EndpointsContextMenu"
    const val ENDPOINTS_FILTER_TOOLBAR_PLACE: String = "EndpointsFilterToolbar"

    @Topic.ProjectLevel
    val TOPIC: Topic<EndpointsViewOpener> = Topic(EndpointsViewOpener::class.java)

    @JvmStatic
    fun isAvailable(project: Project): Boolean {
      val toolWindow = getInstance(project).getToolWindow(ENDPOINTS_TOOLWINDOW_ID)
      return toolWindow != null && toolWindow.isAvailable
    }

    @JvmStatic
    fun showAllEndpoints(project: Project) {
      showEndpointsWithFilter(project, null)
    }

    @JvmStatic
    fun showEndpointsWithFilter(project: Project, module: String?, framework: String?, filter: String?) {
      project.messageBus.syncPublisher(TOPIC).showEndpoints(module, framework, filter)
    }

    @JvmStatic
    fun showEndpointsWithFilter(project: Project, filter: String?) {
      project.messageBus.syncPublisher(TOPIC).showEndpoints(filter)
    }
  }
}