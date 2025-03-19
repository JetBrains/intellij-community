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
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.UiDataProvider
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel
import org.jetbrains.jewel.bridge.actionSystem.ComponentDataProviderBridge
import org.jetbrains.jewel.bridge.theme.SwingBridgeTheme
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.InternalJewelApi

@Suppress("ktlint:standard:function-naming", "FunctionName") // Swing to Compose bridge API
public fun JewelComposePanel(config: ComposePanel.() -> Unit = {}, content: @Composable () -> Unit): JComponent =
    createJewelComposePanel { jewelPanel ->
        config()
        setContent {
            SwingBridgeTheme {
                CompositionLocalProvider(LocalComponent provides this@createJewelComposePanel) {
                    ComponentDataProviderBridge(jewelPanel, content = content)
                }
            }
        }
    }

@InternalJewelApi
@Suppress("ktlint:standard:function-naming", "FunctionName") // Swing to Compose bridge API
public fun JewelToolWindowComposePanel(
    config: ComposePanel.() -> Unit = {},
    content: @Composable () -> Unit,
): JComponent = createJewelComposePanel { jewelPanel ->
    config()
    setContent {
        Compose17IJSizeBugWorkaround {
            SwingBridgeTheme {
                CompositionLocalProvider(LocalComponent provides this@createJewelComposePanel) {
                    ComponentDataProviderBridge(jewelPanel, content = content)
                }
            }
        }
    }
}

@ExperimentalJewelApi
@Suppress("ktlint:standard:function-naming", "FunctionName") // Swing to Compose bridge API
public fun JewelComposeNoThemePanel(config: ComposePanel.() -> Unit = {}, content: @Composable () -> Unit): JComponent =
    createJewelComposePanel { jewelPanel ->
        config()
        setContent {
            CompositionLocalProvider(LocalComponent provides this@createJewelComposePanel) {
                ComponentDataProviderBridge(jewelPanel, content = content)
            }
        }
    }

@ExperimentalJewelApi
@Suppress("ktlint:standard:function-naming", "FunctionName") // Swing to Compose bridge API
public fun JewelToolWindowNoThemeComposePanel(
    config: ComposePanel.() -> Unit = {},
    content: @Composable () -> Unit,
): JComponent = createJewelComposePanel { jewelPanel ->
    config()
    setContent {
        Compose17IJSizeBugWorkaround {
            CompositionLocalProvider(LocalComponent provides this@createJewelComposePanel) {
                ComponentDataProviderBridge(jewelPanel, content = content)
            }
        }
    }
}

private fun createJewelComposePanel(config: ComposePanel.(JewelComposePanel) -> Unit): JewelComposePanel {
    val jewelPanel = JewelComposePanel()
    jewelPanel.layout = BorderLayout()
    val composePanel = ComposePanel()
    jewelPanel.add(composePanel, BorderLayout.CENTER)
    composePanel.config(jewelPanel)
    return jewelPanel
}

internal class JewelComposePanel : JPanel(), UiDataProvider {
    internal var targetProvider: UiDataProvider? = null

    override fun uiDataSnapshot(sink: DataSink) {
        targetProvider?.uiDataSnapshot(sink)
    }
}

@ExperimentalJewelApi
public val LocalComponent: ProvidableCompositionLocal<JComponent> = staticCompositionLocalOf {
    error("CompositionLocal LocalComponent not provided")
}

/**
 * Workaround until the issue with Compose 1.7 + fillMax__ + IntelliJ Panels is fixed:
 * https://github.com/JetBrains/jewel/issues/504 https://youtrack.jetbrains.com/issue/CMP-5856.
 */
@Composable
private fun Compose17IJSizeBugWorkaround(content: @Composable () -> Unit) {
    with(LocalDensity.current) {
        Box(modifier = Modifier.requiredSize(LocalWindowInfo.current.containerSize.toSize().toDpSize())) { content() }
    }
}
