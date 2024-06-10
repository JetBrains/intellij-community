package org.jetbrains.jewel.foundation.theme

import androidx.compose.runtime.Immutable

@Immutable
public interface ThemeDescriptor {
    public val name: String
    public val isDark: Boolean
    public val colors: ThemeColorPalette
    public val iconData: ThemeIconData
}
