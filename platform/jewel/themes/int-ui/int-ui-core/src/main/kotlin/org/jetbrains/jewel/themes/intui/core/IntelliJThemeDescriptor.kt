package org.jetbrains.jewel.themes.intui.core

interface IntelliJThemeDescriptor {

    val name: String
    val isDark: Boolean
    val colors: IntelliJThemeColorPalette
    val icons: IntelliJThemeIcons
}
