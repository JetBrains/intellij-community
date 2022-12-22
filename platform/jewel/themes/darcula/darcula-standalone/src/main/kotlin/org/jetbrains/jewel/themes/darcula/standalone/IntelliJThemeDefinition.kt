package org.jetbrains.jewel.themes.darcula.standalone

import androidx.compose.runtime.Composable
import org.jetbrains.jewel.IntelliJMetrics
import org.jetbrains.jewel.IntelliJPainters
import org.jetbrains.jewel.IntelliJPalette
import org.jetbrains.jewel.IntelliJTypography
import org.jetbrains.jewel.IntelliJTheme as BaseIntelliJTheme

@Composable
fun IntelliJTheme(isDark: Boolean, content: @Composable () -> Unit) =
    BaseIntelliJTheme(
        if (isDark) IntelliJPalette.darcula else IntelliJPalette.light,
        IntelliJMetrics.default,
        if (isDark) IntelliJPainters.darcula else IntelliJPainters.light,
        if (isDark) IntelliJTypography.darcula else IntelliJTypography.light,
        content
    )

@Composable
fun IntelliJThemeLight(content: @Composable () -> Unit) =
    BaseIntelliJTheme(
        IntelliJPalette.light,
        IntelliJMetrics.default,
        IntelliJPainters.light,
        IntelliJTypography.light,
        content
    )

@Composable
fun IntelliJThemeDark(content: @Composable () -> Unit) =
    BaseIntelliJTheme(
        IntelliJPalette.darcula,
        IntelliJMetrics.default,
        IntelliJPainters.darcula,
        IntelliJTypography.darcula,
        content
    )
