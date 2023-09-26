package org.jetbrains.jewel.bridge

import androidx.compose.runtime.Composable
import androidx.compose.ui.awt.ComposePanel
import com.intellij.openapi.wm.ToolWindow
import org.jetbrains.jewel.foundation.enableNewSwingCompositing

fun ToolWindow.addComposeTab(
    tabDisplayName: String,
    isLockable: Boolean = true,
    isCloseable: Boolean = false,
    content: @Composable () -> Unit,
) {
    // We need to make sure this is done before Compose is attached.
    // The operation is idempotent, so we can safely do it every time.
    enableNewSwingCompositing()

    val composePanel = ComposePanel()
    composePanel.setContent(content)
    val tabContent = contentManager.factory.createContent(composePanel, tabDisplayName, isLockable)
    tabContent.isCloseable = isCloseable
    contentManager.addContent(tabContent)
}
