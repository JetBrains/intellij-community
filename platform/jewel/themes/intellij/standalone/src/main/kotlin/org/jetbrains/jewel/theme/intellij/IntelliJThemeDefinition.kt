package org.jetbrains.jewel.theme.intellij

import androidx.compose.runtime.Composable

@Composable
fun IntelliJThemeLight(content: @Composable () -> Unit) =
    IntelliJTheme(
        IntelliJPalette.light,
        IntelliJMetrics.default,
        IntelliJPainters.light,
        IntelliJTypography.default,
        content
    )

@Composable
fun IntelliJThemeDark(content: @Composable () -> Unit) =
    IntelliJTheme(
        IntelliJPalette.darcula,
        IntelliJMetrics.default,
        IntelliJPainters.darcula,
        IntelliJTypography.default,
        content
    )
