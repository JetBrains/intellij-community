package org.jetbrains.jewel.foundation.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import org.jetbrains.jewel.foundation.GlobalColors
import org.jetbrains.jewel.foundation.GlobalMetrics
import org.jetbrains.jewel.foundation.LocalGlobalColors
import org.jetbrains.jewel.foundation.LocalGlobalMetrics

interface JewelTheme {

    companion object {

        val globalColors: GlobalColors
            @Composable
            @ReadOnlyComposable
            get() = LocalGlobalColors.current

        val globalMetrics: GlobalMetrics
            @Composable
            @ReadOnlyComposable
            get() = LocalGlobalMetrics.current

        val textStyle: TextStyle
            @Composable
            @ReadOnlyComposable
            get() = LocalTextStyle.current

        val contentColor: Color
            @Composable
            @ReadOnlyComposable
            get() = LocalContentColor.current

        val isDark: Boolean
            @Composable
            @ReadOnlyComposable
            get() = LocalIsDarkTheme.current

        val isSwingCompatMode
            @Composable
            @ReadOnlyComposable
            get() = LocalSwingCompatMode.current
    }
}

@Composable
fun JewelTheme(
    theme: ThemeDefinition,
    swingCompatMode: Boolean,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalSwingCompatMode provides swingCompatMode) {
        JewelTheme(theme, content)
    }
}

@Composable
fun JewelTheme(theme: ThemeDefinition, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalIsDarkTheme provides theme.isDark,
        LocalContentColor provides theme.contentColor,
        LocalTextStyle provides theme.defaultTextStyle,
        LocalGlobalColors provides theme.globalColors,
        LocalGlobalMetrics provides theme.globalMetrics,
        content = content,
    )
}

val LocalContentColor = staticCompositionLocalOf<Color> {
    error("No ContentColor provided")
}

internal val LocalIsDarkTheme = staticCompositionLocalOf<Boolean> {
    error("No IsDarkTheme provided")
}

internal val LocalSwingCompatMode = staticCompositionLocalOf {
    // By default, Swing compat is not enabled
    false
}

val LocalColorPalette = staticCompositionLocalOf {
    ThemeColorPalette.Empty
}

val LocalIconData = staticCompositionLocalOf {
    ThemeIconData.Empty
}

val LocalTextStyle = staticCompositionLocalOf<TextStyle> {
    error("No TextStyle provided")
}

/** Overrides the dark mode for the current composition scope. */
@Composable
fun OverrideDarkMode(isDark: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalIsDarkTheme provides isDark, content = content)
}
