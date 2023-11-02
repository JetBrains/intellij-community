package org.jetbrains.jewel.foundation.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import org.jetbrains.jewel.foundation.GlobalColors
import org.jetbrains.jewel.foundation.GlobalMetrics
import org.jetbrains.jewel.foundation.LocalGlobalColors
import org.jetbrains.jewel.foundation.LocalGlobalMetrics

public interface JewelTheme {

    public companion object {

        public val globalColors: GlobalColors
            @Composable
            @ReadOnlyComposable
            get() = LocalGlobalColors.current

        public val globalMetrics: GlobalMetrics
            @Composable
            @ReadOnlyComposable
            get() = LocalGlobalMetrics.current

        public val textStyle: TextStyle
            @Composable
            @ReadOnlyComposable
            get() = LocalTextStyle.current

        public val contentColor: Color
            @Composable
            @ReadOnlyComposable
            get() = LocalContentColor.current

        public val isDark: Boolean
            @Composable
            @ReadOnlyComposable
            get() = LocalIsDarkTheme.current

        public val isSwingCompatMode: Boolean
            @Composable
            @ReadOnlyComposable
            get() = LocalSwingCompatMode.current
    }
}

@Composable
public fun JewelTheme(
    theme: ThemeDefinition,
    swingCompatMode: Boolean,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalSwingCompatMode provides swingCompatMode) {
        JewelTheme(theme, content)
    }
}

@Composable
public fun JewelTheme(theme: ThemeDefinition, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalIsDarkTheme provides theme.isDark,
        LocalContentColor provides theme.contentColor,
        LocalTextStyle provides theme.defaultTextStyle,
        LocalGlobalColors provides theme.globalColors,
        LocalGlobalMetrics provides theme.globalMetrics,
        content = content,
    )
}

public val LocalContentColor: ProvidableCompositionLocal<Color> =
    staticCompositionLocalOf {
        error("No ContentColor provided. Have you forgotten the theme?")
    }

internal val LocalIsDarkTheme: ProvidableCompositionLocal<Boolean> =
    staticCompositionLocalOf {
        error("No IsDarkTheme provided. Have you forgotten the theme?")
    }

internal val LocalSwingCompatMode: ProvidableCompositionLocal<Boolean> =
    staticCompositionLocalOf {
        // By default, Swing compat is not enabled
        false
    }

public val LocalColorPalette: ProvidableCompositionLocal<ThemeColorPalette> =
    staticCompositionLocalOf {
        ThemeColorPalette.Empty
    }

public val LocalIconData: ProvidableCompositionLocal<ThemeIconData> = staticCompositionLocalOf {
    ThemeIconData.Empty
}

public val LocalTextStyle: ProvidableCompositionLocal<TextStyle> =
    staticCompositionLocalOf {
        error("No TextStyle provided. Have you forgotten the theme?")
    }

/**
 * Overrides the dark mode for the current composition scope.
 */
@Composable
public fun OverrideDarkMode(isDark: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalIsDarkTheme provides isDark, content = content)
}
