package org.jetbrains.jewel.bridge

import androidx.compose.runtime.Composable
import com.intellij.openapi.util.NlsContexts.TabTitle
import com.intellij.openapi.wm.ToolWindow
import org.jetbrains.jewel.foundation.enableNewSwingCompositing

/**
 * Adds a new tab to the tool window with Compose content.
 *
 * @param tabDisplayName The title of the tab.
 * @param isLockable Whether the tab can be locked.
 * @param isCloseable Whether the tab can be closed.
 * @param content The Composable content of the tab.
 */
public fun ToolWindow.addComposeTab(
    @TabTitle tabDisplayName: String? = null,
    isLockable: Boolean = true,
    isCloseable: Boolean = false,
    content: @Composable ToolWindowScope.() -> Unit,
) {
    // We need to make sure this is done before Compose is attached.
    // The operation is idempotent, so we can safely do it every time.
    enableNewSwingCompositing()

    val tabContent =
        contentManager.factory.createContent(
            JewelComposePanel {
                val scope =
                    object : ToolWindowScope {
                        override val toolWindow: ToolWindow
                            get() = this@addComposeTab
                    }
                scope.content()
            },
            tabDisplayName,
            isLockable,
        )
    tabContent.isCloseable = isCloseable
    contentManager.addContent(tabContent)
}

/** A scope for the content of a tool window tab. */
public interface ToolWindowScope {
    /** The tool window in which the tab is displayed. */
    public val toolWindow: ToolWindow
}
