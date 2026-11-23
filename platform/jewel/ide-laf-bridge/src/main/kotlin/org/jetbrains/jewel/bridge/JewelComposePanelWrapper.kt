package org.jetbrains.jewel.bridge

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.awt.ComposePanel
import com.intellij.diagnostic.PluginException
import com.intellij.ide.KeyboardAwareContainer
import com.intellij.ide.plugins.PluginUtil
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.platform.ide.productMode.IdeProductMode
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.AWTEvent
import java.awt.Component
import java.awt.Toolkit
import java.awt.event.AWTEventListener
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import javax.swing.JComponent
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.bridge.actionSystem.ComponentDataProviderBridge
import org.jetbrains.jewel.bridge.actionSystem.JEWEL_SHORTCUT_HOST_STATE
import org.jetbrains.jewel.bridge.component.JBPopupRenderer
import org.jetbrains.jewel.bridge.theme.SwingBridgeTheme
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.InternalJewelApi
import org.jetbrains.jewel.foundation.LocalComponent as LocalComponentFoundation
import org.jetbrains.jewel.foundation.shortcut.JewelShortcutHostState
import org.jetbrains.jewel.foundation.shortcut.ProvideJewelShortcutHost
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
                    LocalPopupRenderer provides JBPopupRenderer,
                ) {
                    ShortcutHostBridge(jewelPanel) { ComponentDataProviderBridge(jewelPanel, content = content) }
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
                ShortcutHostBridge(jewelPanel) { ComponentDataProviderBridge(jewelPanel, content = content) }
            }
        }
    }

internal fun composePopupPanel(
    focusOnClickInside: Boolean = false,
    shortcutHost: JewelShortcutHostState? = null,
    installShortcutBridge: Boolean = true,
    content: @Composable () -> Unit,
): JewelComposePanelWrapper =
    createJewelComposePanel(focusOnClickInside) { jewelPanel ->
            setContent {
                SwingBridgeTheme {
                    CompositionLocalProvider(
                        LocalComponentFoundation provides this@createJewelComposePanel,
                        LocalPopupRenderer provides JBPopupRenderer,
                    ) {
                        when {
                            !installShortcutBridge -> ComponentDataProviderBridge(jewelPanel, content = content)
                            shortcutHost == null ->
                                ShortcutHostBridge(jewelPanel) {
                                    ComponentDataProviderBridge(jewelPanel, content = content)
                                }
                            else ->
                                ProvideJewelShortcutHost(shortcutHost) {
                                    ComponentDataProviderBridge(jewelPanel, content = content)
                                }
                        }
                    }
                }
            }
        }
        .apply { isPopupWrapper = true }

private fun createJewelComposePanel(
    focusOnClickInside: Boolean,
    config: ComposePanel.(JewelComposePanelWrapper) -> Unit,
): JewelComposePanelWrapper {
    if (IdeProductMode.isBackend) {
        val causePluginId = PluginUtil.getInstance().findPluginId(Throwable("Detecting Guilty Plugin"))
        throw PluginException(
            "Backend IDE mode does not support Compose UI and Jewel Components. Split the plugin to .frontend and .backend modules. " +
                "See https://plugins.jetbrains.com/docs/intellij/split-mode-and-remote-development.html",
            causePluginId,
        )
    }

    val jewelPanel = JewelComposePanelWrapper(focusOnClickInside)
    jewelPanel.composePanel.config(jewelPanel)
    ComposeUiInspector(jewelPanel)
    return jewelPanel
}

@ApiStatus.Internal
@InternalJewelApi
public class JewelComposePanelWrapper(private val focusOnClickInside: Boolean) :
    BorderLayoutPanel(), UiDataProvider, KeyboardAwareContainer {
    internal var targetProvider: UiDataProvider? = null

    /**
     * Evaluates whether a focused Jewel claim owns a key event, set by the shortcut integration during content
     * composition. The actual AWT focus owner inside a [ComposePanel] is an internal skiko component that cannot
     * implement [com.intellij.ide.KeyboardAwareFocusOwner], so the platform consults this wrapper — an ancestor of that
     * focus owner — instead. Must be fast: called on the EDT for every key event while a descendant is focused.
     */
    @Volatile internal var shortcutClaimEvaluator: ((KeyEvent) -> Boolean)? = null

    /** The shortcut host wired by [org.jetbrains.jewel.bridge.ShortcutHostBridge]; used by bridge actions. */
    @Volatile
    public var shortcutHostState: org.jetbrains.jewel.foundation.shortcut.JewelShortcutHostState? = null
        internal set

    /** Panel-scoped SelectAll bridge action registered by [installBridgeClaimLane] to bridge `$SelectAll`. */
    @Volatile internal var selectAllBridgeAction: AnAction? = null

    /** True when this wrapper hosts a popup surface created by [composePopupPanel]. */
    @Volatile internal var isPopupWrapper: Boolean = false

    /**
     * Stack of active focusable `JBPopup` wrappers owned by this panel (topmost last). Because `JBPopup` keeps AWT
     * focus on the owner panel, context resolution consults the active popup wrapper so edit providers and bridge
     * actions target the popup's Compose surface instead of the background panel.
     */
    private val activePopupWrappers = ArrayDeque<JewelComposePanelWrapper>()

    internal fun pushActivePopupWrapper(popupWrapper: JewelComposePanelWrapper) {
        synchronized(activePopupWrappers) {
            activePopupWrappers.remove(popupWrapper)
            activePopupWrappers.addLast(popupWrapper)
        }
    }

    internal fun popActivePopupWrapper(popupWrapper: JewelComposePanelWrapper) {
        synchronized(activePopupWrappers) { activePopupWrappers.remove(popupWrapper) }
    }

    internal fun topActivePopupWrapper(): JewelComposePanelWrapper? =
        synchronized(activePopupWrappers) { activePopupWrappers.lastOrNull() }

    internal fun activePopupPanels(): Set<Component> =
        synchronized(activePopupWrappers) {
            if (activePopupWrappers.isEmpty()) emptySet() else activePopupWrappers.mapTo(HashSet()) { it.composePanel }
        }

    override fun skipKeyEventDispatcher(focusOwner: Component, event: KeyEvent): Boolean =
        shortcutClaimEvaluator?.invoke(event) == true

    private val listener = AWTEventListener { event ->
        if (event !is MouseEvent || event.button == MouseEvent.NOBUTTON) return@AWTEventListener
        if (!composePanel.isFocusOwner && event.component.parent == composePanel) {
            composePanel.requestFocus()
        }
    }

    public val composePanel: ComposePanel = ComposePanel()

    init {
        super.addToCenter(composePanel)
        composePanel.isClearFocusOnMouseDownEnabled = false
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
        shortcutHostState?.let { sink[JEWEL_SHORTCUT_HOST_STATE] = it }
        targetProvider?.uiDataSnapshot(sink)
    }
}
