package org.jetbrains.jewel.bridge.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.keymap.Keymap
import com.intellij.openapi.keymap.KeymapManagerListener
import com.intellij.util.ui.EDT
import javax.swing.SwingUtilities
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.bridge.BridgeMessageResourceResolver
import org.jetbrains.jewel.bridge.BridgePainterHintsProvider
import org.jetbrains.jewel.bridge.BridgePlatformCursorController
import org.jetbrains.jewel.bridge.BridgeTypography
import org.jetbrains.jewel.bridge.BridgeUriHandler
import org.jetbrains.jewel.bridge.SwingBridgeReader
import org.jetbrains.jewel.bridge.clipboard.JewelBridgeClipboard
import org.jetbrains.jewel.bridge.icon.BridgeNewUiChecker
import org.jetbrains.jewel.bridge.menuShortcut.BridgeMenuItemShortcutHintProvider
import org.jetbrains.jewel.bridge.menuShortcut.BridgeMenuItemShortcutProvider
import org.jetbrains.jewel.bridge.scaleDensityWithIdeScale
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.ui.ComponentStyling
import org.jetbrains.jewel.ui.LocalMenuItemShortcutHintProvider
import org.jetbrains.jewel.ui.LocalMenuItemShortcutProvider
import org.jetbrains.jewel.ui.LocalTypography
import org.jetbrains.jewel.ui.MenuItemShortcutHintProvider
import org.jetbrains.jewel.ui.MenuItemShortcutProvider
import org.jetbrains.jewel.ui.icon.LocalNewUiChecker
import org.jetbrains.jewel.ui.painter.LocalPainterHintsProvider
import org.jetbrains.jewel.ui.platform.LocalPlatformCursorController
import org.jetbrains.jewel.ui.theme.BaseJewelTheme
import org.jetbrains.jewel.ui.util.LocalMessageResourceResolverProvider

private val bridgeThemeReader by lazy { SwingBridgeReader() }

/**
 * Entry-point composable for using Jewel inside an IntelliJ Platform plugin. Reads the current IntelliJ theme via
 * [SwingBridgeReader] and applies it as a [BaseJewelTheme] with Swing compatibility mode enabled and the appropriate
 * platform bridge providers wired up.
 *
 * @param content The composable content to render under the bridged theme.
 */
@ApiStatus.Experimental
@ExperimentalJewelApi
@Composable
public fun SwingBridgeTheme(content: @Composable () -> Unit) {
    val themeData by bridgeThemeReader.currentBridgeThemeData.collectAsState()
    var keymapRevision by remember { mutableIntStateOf(0) }

    DisposableEffect(Unit) {
        val app = ApplicationManager.getApplication()
        val connection =
            if (app != null && !app.isDisposed) {
                val bumpRevision = {
                    if (EDT.isCurrentThreadEdt()) {
                        keymapRevision++
                    } else {
                        SwingUtilities.invokeLater { keymapRevision++ }
                    }
                }
                app.messageBus.connect().apply {
                    subscribe(
                        KeymapManagerListener.TOPIC,
                        object : KeymapManagerListener {
                            override fun activeKeymapChanged(keymap: Keymap?) {
                                bumpRevision()
                            }

                            override fun shortcutChanged(keymap: Keymap, actionId: String) {
                                bumpRevision()
                            }

                            override fun shortcutChanged(keymap: Keymap, actionId: String, fromSettings: Boolean) {
                                bumpRevision()
                            }

                            override fun shortcutsChanged(
                                keymap: Keymap,
                                actionIds: Collection<String>,
                                fromSettings: Boolean,
                            ) {
                                bumpRevision()
                            }
                        },
                    )
                }
            } else {
                null
            }
        onDispose { connection?.disconnect() }
    }

    val menuItemShortcutProvider =
        remember(keymapRevision) { object : MenuItemShortcutProvider by BridgeMenuItemShortcutProvider {} }
    val menuItemShortcutHintProvider =
        remember(keymapRevision) { object : MenuItemShortcutHintProvider by BridgeMenuItemShortcutHintProvider {} }

    BaseJewelTheme(
        themeData.themeDefinition,
        ComponentStyling.with(themeData.componentStyling),
        swingCompatMode = true,
    ) {
        CompositionLocalProvider(
            LocalPainterHintsProvider provides
                remember(themeData.themeDefinition.isDark) {
                    BridgePainterHintsProvider(themeData.themeDefinition.isDark)
                },
            LocalNewUiChecker provides BridgeNewUiChecker,
            LocalDensity provides scaleDensityWithIdeScale(LocalDensity.current),
            LocalClipboard provides remember { JewelBridgeClipboard() },
            LocalMenuItemShortcutProvider provides menuItemShortcutProvider,
            LocalMenuItemShortcutHintProvider provides menuItemShortcutHintProvider,
            LocalTypography provides BridgeTypography,
            LocalUriHandler provides BridgeUriHandler,
            LocalMessageResourceResolverProvider provides remember { BridgeMessageResourceResolver() },
            LocalPlatformCursorController provides BridgePlatformCursorController,
        ) {
            content()
        }
    }
}
