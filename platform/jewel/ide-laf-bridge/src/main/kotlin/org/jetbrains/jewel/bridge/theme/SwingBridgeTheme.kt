package org.jetbrains.jewel.bridge.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalDensity
import org.jetbrains.jewel.bridge.BridgePainterHintsProvider
import org.jetbrains.jewel.bridge.SwingBridgeService
import org.jetbrains.jewel.bridge.icon.BridgeNewUiChecker
import org.jetbrains.jewel.bridge.scaleDensityWithIdeScale
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.ui.ComponentStyling
import org.jetbrains.jewel.ui.icon.LocalNewUiChecker
import org.jetbrains.jewel.ui.painter.LocalPainterHintsProvider
import org.jetbrains.jewel.ui.theme.BaseJewelTheme

private val bridgeService by lazy { SwingBridgeService() }

@ExperimentalJewelApi
@Composable
public fun SwingBridgeTheme(content: @Composable () -> Unit) {
    val themeData by bridgeService.currentBridgeThemeData.collectAsState()

    BaseJewelTheme(
        themeData.themeDefinition,
        ComponentStyling.with(themeData.componentStyling),
        swingCompatMode = true,
    ) {
        CompositionLocalProvider(
            LocalPainterHintsProvider provides BridgePainterHintsProvider(themeData.themeDefinition.isDark),
            LocalNewUiChecker provides BridgeNewUiChecker,
            LocalDensity provides scaleDensityWithIdeScale(LocalDensity.current),
        ) {
            content()
        }
    }
}
