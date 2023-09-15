package com.intellij.importSettings.chooser.bla

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

class OpenTelemetryToolWindowFactory: ToolWindowFactory, DumbAware {
    companion object {
        const val OPEN_TELEMETRY_TOOL_WINDOW = "OpenTelemetry"
    }

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val contentFactory = ContentFactory.getInstance()

        val metricsTab = MetricTab()
        val metricsContent = contentFactory.createContent(metricsTab, "Metrics", true)
        metricsContent.isCloseable = false
        toolWindow.contentManager.addContent(metricsContent)

    }
}
