package org.jetbrains.jewel.bridge

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.ui.awt.ComposePanel
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.application.PathManager
import java.awt.BorderLayout
import java.awt.Component
import java.io.File
import javax.swing.JComponent
import javax.swing.JPanel
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.bridge.actionSystem.ComponentDataProviderBridge
import org.jetbrains.jewel.bridge.component.JBPopupRenderer
import org.jetbrains.jewel.bridge.theme.SwingBridgeTheme
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.LocalComponent as LocalComponentFoundation
import org.jetbrains.jewel.foundation.util.JewelLogger
import org.jetbrains.jewel.ui.component.LocalPopupRenderer
import org.jetbrains.jewel.ui.util.LocalMessageResourceResolverProvider

/**
 * Creates a Swing component that can host Compose content.
 *
 * The [content] is wrapped in a [SwingBridgeTheme], which will be derived from the current Swing LaF.
 *
 * @param config A lambda to configure the underlying [ComposePanel].
 * @param content The Composable content to display.
 */
public fun compose(config: ComposePanel.() -> Unit = {}, content: @Composable () -> Unit): JComponent =
    JewelComposePanel(config, content)

/**
 * Creates a Swing component that can host Compose content.
 *
 * The [content] is wrapped in a [SwingBridgeTheme], which will be derived from the current Swing LaF.
 *
 * This is the same as [compose].
 *
 * @param config A lambda to configure the underlying [ComposePanel].
 * @param content The Composable content to display.
 */
@Suppress("ktlint:standard:function-naming", "FunctionName") // Swing to Compose bridge API
public fun JewelComposePanel(config: ComposePanel.() -> Unit = {}, content: @Composable () -> Unit): JComponent =
    createJewelComposePanel { jewelPanel ->
        config()
        setContent {
            SwingBridgeTheme {
                CompositionLocalProvider(
                    LocalComponentFoundation provides this@createJewelComposePanel,
                    LocalPopupRenderer provides JBPopupRenderer,
                ) {
                    ComponentDataProviderBridge(jewelPanel, content = content)
                }
            }
        }
    }

/**
 * Creates a Swing component that can host Compose content.
 *
 * The [content] is **not** wrapped in a theme, meaning that you **MUST** wrap the content in a theme by yourself.
 *
 * This is not normally what you want; use this only if you want to provide a completely custom theme.
 *
 * @param config A lambda to configure the underlying [ComposePanel].
 * @param content The Composable content to display.
 */
@ApiStatus.Experimental
@ExperimentalJewelApi
@Suppress("ktlint:standard:function-naming") // Swing to Compose bridge API
public fun composeWithoutTheme(config: ComposePanel.() -> Unit = {}, content: @Composable () -> Unit): JComponent =
    JewelComposeNoThemePanel(config, content)

/**
 * Creates a Swing component that can host Compose content.
 *
 * The [content] is **not** wrapped in a theme, meaning that you **MUST** wrap the content in a theme by yourself.
 *
 * This is not normally what you want; use this only if you want to provide a completely custom theme.
 *
 * This is the same as [composeWithoutTheme].
 *
 * @param config A lambda to configure the underlying [ComposePanel].
 * @param content The Composable content to display.
 */
@ApiStatus.Experimental
@ExperimentalJewelApi
@Suppress("ktlint:standard:function-naming", "FunctionName") // Swing to Compose bridge API
public fun JewelComposeNoThemePanel(config: ComposePanel.() -> Unit = {}, content: @Composable () -> Unit): JComponent =
    createJewelComposePanel { jewelPanel ->
        config()
        setContent {
            CompositionLocalProvider(
                LocalComponentFoundation provides this@createJewelComposePanel,
                LocalPopupRenderer provides JBPopupRenderer,
                LocalMessageResourceResolverProvider provides BridgeMessageResourceResolver(),
            ) {
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

    val composePanel: ComposePanel
        get() =
            components.singleOrNull() as? ComposePanel
                ?: error("JewelComposePanelWrapper was not initialized with a ComposePanel")

    override fun addImpl(comp: Component, constraints: Any?, index: Int) {
        require(components.isEmpty()) {
            "JewelComposePanelWrapper can only contain a single ComposePanel, attempt to add another component"
        }

        require(comp is ComposePanel) {
            "JewelComposePanelWrapper can only contain ComposePanel, attempt to add ${comp::class.java.name}"
        }

        super.addImpl(comp, constraints, index)
    }

    override fun uiDataSnapshot(sink: DataSink) {
        targetProvider?.uiDataSnapshot(sink)
    }
}

/** Provides the root component used to host the current Compose hierarchy. */
@Suppress("CompositionLocalAllowlist")
@ApiStatus.Experimental
@ExperimentalJewelApi
@Deprecated(
    "Use the LocalComponent from the foundation API",
    replaceWith = ReplaceWith("LocalComponent", "org.jetbrains.jewel.foundation.LocalComponent"),
)
public val LocalComponent: ProvidableCompositionLocal<JComponent> = LocalComponentFoundation
