package org.jetbrains.jewel.bridge

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.awt.ComposePanel
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.application.PathManager
import java.awt.BorderLayout
import java.io.File
import javax.swing.JComponent
import javax.swing.JPanel
import org.jetbrains.jewel.bridge.actionSystem.ComponentDataProviderBridge
import org.jetbrains.jewel.bridge.theme.SwingBridgeTheme
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.InternalJewelApi
import org.jetbrains.jewel.foundation.util.JewelLogger

public fun compose(config: ComposePanel.() -> Unit = {}, content: @Composable () -> Unit): JComponent =
    JewelComposePanel(config, content)

@ExperimentalJewelApi
@Suppress("ktlint:standard:function-naming", "FunctionName") // Swing to Compose bridge API
public fun JBPanel(config: ComposePanel.() -> Unit = {}, content: @Composable () -> Unit): JComponent =
    JewelComposePanel(config, content)

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
        SwingBridgeTheme {
            CompositionLocalProvider(LocalComponent provides this@createJewelComposePanel) {
                ComponentDataProviderBridge(jewelPanel, content = content)
            }
        }
    }
}

@ExperimentalJewelApi
@Suppress("ktlint:standard:function-naming") // Swing to Compose bridge API
public fun composeWithoutTheme(config: ComposePanel.() -> Unit = {}, content: @Composable () -> Unit): JComponent =
    JewelComposeNoThemePanel(config, content)

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
@Suppress("ktlint:standard:function-naming") // Swing to Compose bridge API
public fun composeForToolWindowWithoutTheme(
    config: ComposePanel.() -> Unit = {},
    content: @Composable () -> Unit,
): JComponent = JewelToolWindowNoThemeComposePanel(config, content)

@ExperimentalJewelApi
@Suppress("ktlint:standard:function-naming", "FunctionName") // Swing to Compose bridge API
public fun JewelToolWindowNoThemeComposePanel(
    config: ComposePanel.() -> Unit = {},
    content: @Composable () -> Unit,
): JComponent = createJewelComposePanel { jewelPanel ->
    config()
    setContent {
        CompositionLocalProvider(LocalComponent provides this@createJewelComposePanel) {
            ComponentDataProviderBridge(jewelPanel, content = content)
        }
    }
}

private fun createJewelComposePanel(config: ComposePanel.(JewelComposePanelWrapper) -> Unit): JewelComposePanelWrapper {
    if (System.getProperty("skiko.library.path") == null) {
        val bundledSkikoFolder = File(PathManager.getLibPath(), "/skiko-awt-runtime-all")
        if (bundledSkikoFolder.isDirectory && bundledSkikoFolder.canRead()) {
            System.setProperty("skiko.library.path", PathManager.getLibPath() + "/skiko-awt-runtime-all")
        } else {
            JewelLogger.getInstance("SkikoLoader").warn("Bundled Skiko not found/not readable, falling back to default")
        }
    }
    val jewelPanel = JewelComposePanelWrapper()
    jewelPanel.layout = BorderLayout()
    val composePanel = ComposePanel()
    jewelPanel.add(composePanel, BorderLayout.CENTER)
    composePanel.config(jewelPanel)
    ComposeUiInspector(jewelPanel)
    return jewelPanel
}

internal class JewelComposePanelWrapper : JPanel(), UiDataProvider {
    internal var targetProvider: UiDataProvider? = null

    override fun uiDataSnapshot(sink: DataSink) {
        targetProvider?.uiDataSnapshot(sink)
    }
}

@ExperimentalJewelApi
public val LocalComponent: ProvidableCompositionLocal<JComponent> = staticCompositionLocalOf {
    error("CompositionLocal LocalComponent not provided")
}
