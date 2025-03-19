package org.jetbrains.jewel.samples.standalone

import org.jetbrains.skiko.SystemTheme
import org.jetbrains.skiko.currentSystemTheme

public enum class IntUiThemes {
    Light,
    LightWithLightHeader,
    Dark,
    System;

    public fun isDark(): Boolean = (if (this == System) fromSystemTheme(currentSystemTheme) else this) == Dark

    public fun isLightHeader(): Boolean = this == LightWithLightHeader

    public companion object {
        public fun fromSystemTheme(systemTheme: SystemTheme): IntUiThemes =
            if (systemTheme == SystemTheme.LIGHT) Light else Dark
    }
}
