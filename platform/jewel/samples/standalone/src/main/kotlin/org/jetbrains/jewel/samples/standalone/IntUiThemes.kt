package org.jetbrains.jewel.samples.standalone

import org.jetbrains.skiko.SystemTheme
import org.jetbrains.skiko.currentSystemTheme

/** The available Int UI themes for the standalone sample application. */
public enum class IntUiThemes {
    /** The standard light theme. */
    Light,

    /** The light theme with a light-colored title bar header. */
    LightWithLightHeader,

    /** The dark theme. */
    Dark,

    /** Follows the operating system theme setting. */
    System;

    /** Returns `true` if this theme resolves to the dark variant (including when [System] maps to dark). */
    public fun isDark(): Boolean = (if (this == System) fromSystemTheme(currentSystemTheme) else this) == Dark

    /** Returns `true` if this theme uses the light-header title bar variant. */
    public fun isLightHeader(): Boolean = this == LightWithLightHeader

    /** Provides [fromSystemTheme] for mapping the OS theme to a concrete [IntUiThemes] value. */
    public companion object {
        /** Returns [Light] or [Dark] based on the current [systemTheme] reported by Skiko. */
        public fun fromSystemTheme(systemTheme: SystemTheme): IntUiThemes =
            if (systemTheme == SystemTheme.LIGHT) Light else Dark
    }
}
