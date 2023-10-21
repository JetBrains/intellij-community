package org.jetbrains.jewel.intui.core

import androidx.compose.foundation.LocalIndication
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidedValue
import org.jetbrains.jewel.JewelTheme
import org.jetbrains.jewel.LocalColorPalette
import org.jetbrains.jewel.LocalIconData
import org.jetbrains.jewel.NoIndication
import org.jetbrains.jewel.ThemeDefinition

@Composable
fun BaseIntUiTheme(
    theme: ThemeDefinition,
    componentStyling: @Composable () -> Array<ProvidedValue<*>>,
    content: @Composable () -> Unit,
) {
    BaseIntUiTheme(theme, componentStyling, swingCompatMode = false, content)
}

@Composable
fun BaseIntUiTheme(
    theme: ThemeDefinition,
    componentStyling: @Composable () -> Array<ProvidedValue<*>>,
    swingCompatMode: Boolean = false,
    content: @Composable () -> Unit,
) {
    JewelTheme(theme, swingCompatMode) {
        CompositionLocalProvider(
            LocalColorPalette provides theme.colorPalette,
            LocalIconData provides theme.iconData,
            LocalIndication provides NoIndication,
        ) {
            CompositionLocalProvider(values = componentStyling(), content = content)
        }
    }
}
