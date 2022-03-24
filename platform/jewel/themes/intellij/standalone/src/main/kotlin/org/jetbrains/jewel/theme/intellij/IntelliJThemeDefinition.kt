package org.jetbrains.jewel.theme.intellij

import androidx.compose.runtime.Composable

@Composable
fun IntelliJThemeLight(content: @Composable () -> Unit) =
    IntelliJTheme(
        IntelliJPalette.light,
        IntelliJMetrics.default,
        IntelliJPainters.light,
        IntelliJTypography.light,
        content
    )

@Composable
fun IntelliJTheme(isDark: Boolean, content: @Composable () -> Unit) =
    if (isDark) IntelliJThemeDark(content) else IntelliJThemeLight(content)

@Composable
fun IntelliJThemeDark(content: @Composable () -> Unit) =
    IntelliJTheme(
        IntelliJPalette.darcula,
        IntelliJMetrics.default,
        IntelliJPainters.darcula,
        IntelliJTypography.darcula,
        content
    )
