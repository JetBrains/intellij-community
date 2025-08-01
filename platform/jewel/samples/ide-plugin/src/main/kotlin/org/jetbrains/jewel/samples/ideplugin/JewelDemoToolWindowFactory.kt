package org.jetbrains.jewel.samples.ideplugin

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts.TabTitle
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import javax.swing.JComponent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.jetbrains.jewel.bridge.addComposeTab
import org.jetbrains.jewel.foundation.JewelFlags
import org.jetbrains.jewel.samples.ideplugin.releasessample.ReleasesSampleCompose

@Suppress("unused")
@ExperimentalCoroutinesApi
internal class JewelDemoToolWindowFactory : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        // Enable custom popup rendering to use JBPopup instead of default Compose implementation
        JewelFlags.useCustomPopupRenderer = true

        toolWindow.addComposeTab("Components") { ComponentShowcaseTab(project) }

        toolWindow.addComposeTab("Releases Demo") { ReleasesSampleCompose(project) }

        toolWindow.addSwingTab(SwingComparisonTabPanel(), "Swing Comparison")

        toolWindow.addComposeTab("Scrollbars Sample") { ScrollbarsShowcaseTab() }
    }

    private fun ToolWindow.addSwingTab(component: JComponent, @TabTitle title: String) {
        val manager = contentManager
        val tabContent = manager.factory.createContent(component, title, true)
        tabContent.isCloseable = false
        manager.addContent(tabContent)
    }
}
