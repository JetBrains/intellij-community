package org.jetbrains.jewel.bridge

import androidx.compose.runtime.Composable
import androidx.compose.ui.awt.ComposePanel
import com.intellij.openapi.wm.ToolWindow

fun ToolWindow.addComposeTab(
    tabDisplayName: String,
    isLockable: Boolean = true,
    isCloseable: Boolean = false,
    content: @Composable () -> Unit,
) {
    val composePanel = ComposePanel()
    composePanel.setContent(content)
    val tabContent = contentManager.factory.createContent(composePanel, tabDisplayName, isLockable)
    tabContent.isCloseable = isCloseable
    contentManager.addContent(tabContent)
}
