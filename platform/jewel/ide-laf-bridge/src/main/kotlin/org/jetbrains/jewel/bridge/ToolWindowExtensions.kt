package org.jetbrains.jewel.bridge

import androidx.compose.runtime.Composable
import com.intellij.openapi.util.NlsContexts.TabTitle
import com.intellij.openapi.wm.ToolWindow
import org.jetbrains.jewel.foundation.enableNewSwingCompositing

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
            JewelToolWindowComposePanel {
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

public interface ToolWindowScope {
    public val toolWindow: ToolWindow
}
