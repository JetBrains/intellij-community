package org.jetbrains.jewel.themes.darcula.idebridge

import androidx.compose.runtime.Composable
import androidx.compose.ui.awt.ComposePanel
import com.intellij.openapi.wm.ToolWindow

fun ToolWindow.addComposePanel(
    displayName: String,
    isLockable: Boolean = true,
    content: @Composable ComposePanel.() -> Unit
) = ComposePanel(content = content)
    .also { contentManager.addContent(contentManager.factory.createContent(it, displayName, isLockable)) }

internal fun ComposePanel(
    height: Int = 800,
    width: Int = 800,
    y: Int = 0,
    x: Int = 0,
    content: @Composable ComposePanel.() -> Unit
): ComposePanel {
    val panel = ComposePanel()
    panel.setBounds(x, y, width, height)
    panel.setContent {
        panel.content()
    }
    return panel
}
