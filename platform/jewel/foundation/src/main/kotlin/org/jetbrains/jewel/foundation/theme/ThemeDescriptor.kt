package org.jetbrains.jewel.foundation.theme

import androidx.compose.runtime.Immutable

@Immutable
interface ThemeDescriptor {

    val name: String
    val isDark: Boolean
    val colors: ThemeColorPalette
    val iconData: ThemeIconData
}
