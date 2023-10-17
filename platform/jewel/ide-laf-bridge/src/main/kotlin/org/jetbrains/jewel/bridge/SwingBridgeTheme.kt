package org.jetbrains.jewel.bridge

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.intellij.openapi.components.service
import org.jetbrains.jewel.ExperimentalJewelApi
import org.jetbrains.jewel.intui.core.BaseIntUiTheme
import org.jetbrains.jewel.painter.LocalPainterHintsProvider

private val bridgeService
    get() = service<SwingBridgeService>()

@ExperimentalJewelApi
@Composable
fun SwingBridgeTheme(content: @Composable () -> Unit) {
    val themeData by bridgeService.currentBridgeThemeData.collectAsState()

    // TODO handle non-Int UI themes, too
    BaseIntUiTheme(themeData.themeDefinition, {
        themeData.componentStyling.providedStyles()
    }, swingCompatMode = true) {
        CompositionLocalProvider(LocalPainterHintsProvider provides BridgePainterHintsProvider(themeData.themeDefinition.isDark)) {
            content()
        }
    }
}
