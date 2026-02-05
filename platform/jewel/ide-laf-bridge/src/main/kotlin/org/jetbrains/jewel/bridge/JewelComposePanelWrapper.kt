package org.jetbrains.jewel.bridge

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.ui.awt.ComposePanel
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.AWTEvent
import java.awt.Component
import java.awt.Toolkit
import java.awt.event.AWTEventListener
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.SwingUtilities
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.bridge.actionSystem.ComponentDataProviderBridge
import org.jetbrains.jewel.bridge.component.JBPopupRenderer
import org.jetbrains.jewel.bridge.theme.SwingBridgeTheme
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.InternalJewelApi
import org.jetbrains.jewel.foundation.LocalComponent as LocalComponentFoundation
import org.jetbrains.jewel.ui.component.LocalPopupRenderer
import org.jetbrains.jewel.ui.util.LocalMessageResourceResolverProvider

/**
 * Creates a Swing component that can host Compose content.
 *
 * The [content] is wrapped in a [SwingBridgeTheme], which will be derived from the current Swing LaF.
 *
 * @param focusOnClickInside If `true`, the underlying [ComposePanel] will request focus when a mouse click occurs
 *   inside it, even if it does not hit a "focusable" element.
 * @param config A lambda to configure the underlying [ComposePanel].
 * @param content The Composable content to display.
 */
public fun compose(
    focusOnClickInside: Boolean = true,
    config: ComposePanel.() -> Unit = {},
    content: @Composable () -> Unit,
): JComponent = JewelComposePanel(focusOnClickInside, config, content)

/**
 * Creates a Swing component that can host Compose content.
 *
 * The [content] is wrapped in a [SwingBridgeTheme], which will be derived from the current Swing LaF.
 *
 * @param config A lambda to configure the underlying [ComposePanel].
 * @param content The Composable content to display.
 */
@Deprecated("Use the version with 'focusOnClickInside' parameter", level = DeprecationLevel.HIDDEN)
public fun compose(config: ComposePanel.() -> Unit = {}, content: @Composable () -> Unit): JComponent =
    JewelComposePanel(focusOnClickInside = false, config, content)

/**
 * Creates a Swing component that can host Compose content.
 *
 * The [content] is wrapped in a [SwingBridgeTheme], which will be derived from the current Swing LaF.
 *
 * This is the same as [compose].
 *
 * @param focusOnClickInside If `true`, the underlying [ComposePanel] will request focus when a mouse click occurs
 *   inside it, even if it does not hit a "focusable" element.
 * @param config A lambda to configure the underlying [ComposePanel].
 * @param content The Composable content to display.
 */
@Suppress("ktlint:standard:function-naming", "FunctionName") // Swing to Compose bridge API
public fun JewelComposePanel(
    focusOnClickInside: Boolean = true,
    config: ComposePanel.() -> Unit = {},
    content: @Composable () -> Unit,
): JComponent =
    createJewelComposePanel(focusOnClickInside) { jewelPanel ->
        config()
        setContent {
            SwingBridgeTheme {
                CompositionLocalProvider(
                    LocalComponentFoundation provides this@createJewelComposePanel,
                    LocalPopupRenderer provides JBPopupRenderer
                ) {
                    ComponentDataProviderBridge(jewelPanel, content = content)
                }
            }
        }
    }

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
@Deprecated("Use the version with 'focusOnClickInside' parameter", level = DeprecationLevel.HIDDEN)
public fun JewelComposePanel(config: ComposePanel.() -> Unit = {}, content: @Composable () -> Unit): JComponent =
    JewelComposePanel(focusOnClickInside = false, config, content)

/**
 * Creates a Swing component that can host Compose content.
 *
 * The [content] is **not** wrapped in a theme, meaning that you **MUST** wrap the content in a theme by yourself.
 *
 * This is not normally what you want; use this only if you want to provide a completely custom theme.
 *
 * @param focusOnClickInside If `true`, the underlying [ComposePanel] will request focus when a mouse click occurs
 *   inside it, even if it does not hit a "focusable" element.
 * @param config A lambda to configure the underlying [ComposePanel].
 * @param content The Composable content to display.
 */
@ApiStatus.Experimental
@ExperimentalJewelApi
public fun composeWithoutTheme(
    focusOnClickInside: Boolean = true,
    config: ComposePanel.() -> Unit = {},
    content: @Composable () -> Unit,
): JComponent = JewelComposeNoThemePanel(focusOnClickInside, config, content)

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
@Deprecated("Use the version with 'focusOnClickInside' parameter", level = DeprecationLevel.HIDDEN)
public fun composeWithoutTheme(config: ComposePanel.() -> Unit = {}, content: @Composable () -> Unit): JComponent =
    JewelComposeNoThemePanel(focusOnClickInside = false, config, content)

/**
 * Creates a Swing component that can host Compose content.
 *
 * The [content] is **not** wrapped in a theme, meaning that you **MUST** wrap the content in a theme by yourself.
 *
 * This is not normally what you want; use this only if you want to provide a completely custom theme.
 *
 * This is the same as [composeWithoutTheme].
 *
 * @param focusOnClickInside If `true`, the underlying [ComposePanel] will request focus when a mouse click occurs
 *   inside it, even if it does not hit a "focusable" element.
 * @param config A lambda to configure the underlying [ComposePanel].
 * @param content The Composable content to display.
 */
@ApiStatus.Experimental
@ExperimentalJewelApi
@Suppress("ktlint:standard:function-naming", "FunctionName") // Swing to Compose bridge API
public fun JewelComposeNoThemePanel(
    focusOnClickInside: Boolean = true,
    config: ComposePanel.() -> Unit = {},
    content: @Composable () -> Unit,
): JComponent =
    createJewelComposePanel(focusOnClickInside) { jewelPanel ->
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
@Deprecated("Use the version with 'focusOnClickInside' parameter", level = DeprecationLevel.HIDDEN)
public fun JewelComposeNoThemePanel(config: ComposePanel.() -> Unit = {}, content: @Composable () -> Unit): JComponent =
    JewelComposeNoThemePanel(focusOnClickInside = false, config, content)

private fun createJewelComposePanel(
    focusOnClickInside: Boolean,
    config: ComposePanel.(JewelComposePanelWrapper) -> Unit,
): JewelComposePanelWrapper {
    val jewelPanel = JewelComposePanelWrapper(focusOnClickInside)
    jewelPanel.composePanel.config(jewelPanel)
    ComposeUiInspector(jewelPanel)
    return jewelPanel
}

@ApiStatus.Internal
@InternalJewelApi
public class JewelComposePanelWrapper(private val focusOnClickInside: Boolean) : BorderLayoutPanel(), UiDataProvider {
    internal var targetProvider: UiDataProvider? = null
    private val listener = AWTEventListener { event ->
        if (event !is MouseEvent || event.button == MouseEvent.NOBUTTON) return@AWTEventListener
        if (!isFocusOwner && SwingUtilities.isDescendingFrom(event.component, this)) {
            composePanel.requestFocus()
        }
    }

    public val composePanel: ComposePanel = ComposePanel()

    init {
        super.addToCenter(composePanel)
    }

    override fun addImpl(comp: Component, constraints: Any?, index: Int) {
        require(components.isEmpty()) {
            "JewelComposePanelWrapper can only contain a single ComposePanel, attempt to add another component"
        }

        require(comp is ComposePanel) {
            "JewelComposePanelWrapper can only contain ComposePanel, attempt to add ${comp::class.java.name}"
        }

        super.addImpl(comp, constraints, index)
    }

    override fun addNotify() {
        super.addNotify()
        if (focusOnClickInside) {
            Toolkit.getDefaultToolkit().addAWTEventListener(listener, AWTEvent.MOUSE_EVENT_MASK)
        }
    }

    override fun removeNotify() {
        super.removeNotify()
        if (focusOnClickInside) {
            Toolkit.getDefaultToolkit().removeAWTEventListener(listener)
        }
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
