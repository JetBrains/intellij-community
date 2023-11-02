package org.jetbrains.jewel.bridge

import androidx.compose.runtime.Composable
import androidx.compose.ui.awt.ComposePanel
import com.intellij.openapi.wm.ToolWindow
import org.jetbrains.jewel.foundation.enableNewSwingCompositing

public fun ToolWindow.addComposeTab(
    tabDisplayName: String,
    isLockable: Boolean = true,
    isCloseable: Boolean = false,
    content: @Composable ToolWindowScope.() -> Unit,
) {
    // We need to make sure this is done before Compose is attached.
    // The operation is idempotent, so we can safely do it every time.
    enableNewSwingCompositing()

    val composePanel = ComposePanel()

    val scope = object : ToolWindowScope {
        override val toolWindow: ToolWindow = this@addComposeTab
        override val panel: ComposePanel = composePanel
    }

    composePanel.setContent { scope.content() }
    val tabContent = contentManager.factory.createContent(composePanel, tabDisplayName, isLockable)
    tabContent.isCloseable = isCloseable
    contentManager.addContent(tabContent)
}

public interface ToolWindowScope {

    public val toolWindow: ToolWindow

    public val panel: ComposePanel
}
