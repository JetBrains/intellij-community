package org.jetbrains.jewel.bridge.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalDensity
import org.jetbrains.jewel.bridge.BridgePainterHintsProvider
import org.jetbrains.jewel.bridge.BridgeTypography
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
import org.jetbrains.jewel.ui.icon.LocalNewUiChecker
import org.jetbrains.jewel.ui.painter.LocalPainterHintsProvider
import org.jetbrains.jewel.ui.theme.BaseJewelTheme

private val bridgeThemeReader by lazy { SwingBridgeReader() }

@ExperimentalJewelApi
@Composable
public fun SwingBridgeTheme(content: @Composable () -> Unit) {
    val themeData by bridgeThemeReader.currentBridgeThemeData.collectAsState()

    BaseJewelTheme(
        themeData.themeDefinition,
        ComponentStyling.with(themeData.componentStyling),
        swingCompatMode = true,
    ) {
        CompositionLocalProvider(
            LocalPainterHintsProvider provides BridgePainterHintsProvider(themeData.themeDefinition.isDark),
            LocalNewUiChecker provides BridgeNewUiChecker,
            LocalDensity provides scaleDensityWithIdeScale(LocalDensity.current),
            LocalClipboard provides remember { JewelBridgeClipboard() },
            LocalMenuItemShortcutProvider provides BridgeMenuItemShortcutProvider,
            LocalMenuItemShortcutHintProvider provides BridgeMenuItemShortcutHintProvider,
            LocalTypography provides BridgeTypography,
        ) {
            content()
        }
    }
}
