package org.jetbrains.jewel.bridge

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.awt.ComposePanel
import org.jetbrains.jewel.bridge.actionSystem.ComponentDataProviderBridge
import org.jetbrains.jewel.bridge.theme.SwingBridgeTheme
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import javax.swing.JComponent

public fun JewelComposePanel(
    content: @Composable () -> Unit,
): JComponent {
    return ComposePanel().apply {
        setContent {
            SwingBridgeTheme {
                CompositionLocalProvider(LocalComponent provides this@apply) {
                    ComponentDataProviderBridge(this@apply, content = content)
                }
            }
        }
    }
}

@ExperimentalJewelApi
public val LocalComponent: ProvidableCompositionLocal<JComponent> = staticCompositionLocalOf {
    error("CompositionLocal LocalComponent not provided")
}
