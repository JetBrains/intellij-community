package org.jetbrains.jewel.foundation.theme

import androidx.compose.runtime.Immutable

/** Describes the identity and palette of a Jewel theme: its name, dark/light flag, color palette, and icon data. */
@Immutable
public interface ThemeDescriptor {
    /** The unique name identifying this theme. */
    public val name: String

    /** Whether this theme uses a dark color scheme. */
    public val isDark: Boolean

    /** The color palette associated with this theme. */
    public val colors: ThemeColorPalette

    /** The icon data associated with this theme. */
    public val iconData: ThemeIconData
}
