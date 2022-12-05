package org.jetbrains.jewel

data class IntelliJThemeDefinition(
    val palette: IntelliJPalette,
    val metrics: IntelliJMetrics,
    val typography: IntelliJTypography,
    val painters: IntelliJPainters
)
