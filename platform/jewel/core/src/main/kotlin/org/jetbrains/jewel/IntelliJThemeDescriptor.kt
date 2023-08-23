package org.jetbrains.jewel

interface IntelliJThemeDescriptor {

    val name: String
    val isDark: Boolean
    val colors: IntelliJThemeColorPalette
    val icons: IntelliJThemeIconData
}
