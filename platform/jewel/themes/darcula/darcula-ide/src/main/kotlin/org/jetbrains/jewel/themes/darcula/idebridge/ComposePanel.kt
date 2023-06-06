package org.jetbrains.jewel.themes.darcula.idebridge

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposePanel
import com.intellij.openapi.wm.ToolWindow
import org.jetbrains.jewel.components.Surface

fun ToolWindow.addComposePanel(
    tabDisplayName: String,
    isLockable: Boolean = true,
    intelliJThemed: Boolean = true,
    content: @Composable ComposePanel.() -> Unit
) = ComposePanel {
    if (intelliJThemed) {
        IntelliJTheme {
            Surface(Modifier.fillMaxSize()) {
                content()
            }
        }
    } else {
        content()
    }
}.also { contentManager.addContent(contentManager.factory.createContent(it, tabDisplayName, isLockable)) }

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
