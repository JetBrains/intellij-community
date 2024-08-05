package org.jetbrains.jewel.bridge

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposePanel
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.toSize
import org.jetbrains.jewel.bridge.actionSystem.ComponentDataProviderBridge
import org.jetbrains.jewel.bridge.theme.SwingBridgeTheme
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.InternalJewelApi
import javax.swing.JComponent

@Suppress("ktlint:standard:function-naming", "FunctionName") // Swing to Compose bridge API
public fun JewelComposePanel(content: @Composable () -> Unit): JComponent =
    ComposePanel().apply {
        setContent {
            SwingBridgeTheme {
                CompositionLocalProvider(LocalComponent provides this@apply) {
                    ComponentDataProviderBridge(this@apply, content = content)
                }
            }
        }
    }

@InternalJewelApi
@Suppress("ktlint:standard:function-naming", "FunctionName") // Swing to Compose bridge API
public fun JewelToolWindowComposePanel(content: @Composable () -> Unit): JComponent =
    ComposePanel().apply {
        setContent {
            Compose17IJSizeBugWorkaround {
                SwingBridgeTheme {
                    CompositionLocalProvider(LocalComponent provides this@apply) {
                        ComponentDataProviderBridge(this@apply, content = content)
                    }
                }
            }
        }
    }

@ExperimentalJewelApi
public val LocalComponent: ProvidableCompositionLocal<JComponent> =
    staticCompositionLocalOf {
        error("CompositionLocal LocalComponent not provided")
    }

/**
 * Workaround until the issue with Compose 1.7 + fillMax__ + IntelliJ
 * Panels is fixed: https://github.com/JetBrains/jewel/issues/504
 * https://youtrack.jetbrains.com/issue/CMP-5856.
 */
@Composable
private fun Compose17IJSizeBugWorkaround(content: @Composable () -> Unit) {
    with(LocalDensity.current) {
        Box(modifier = Modifier.requiredSize(LocalWindowInfo.current.containerSize.toSize().toDpSize())) {
            content()
        }
    }
}
