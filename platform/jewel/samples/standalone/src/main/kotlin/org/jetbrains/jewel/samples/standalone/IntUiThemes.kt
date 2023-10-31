package org.jetbrains.jewel.samples.standalone

import org.jetbrains.skiko.SystemTheme
import org.jetbrains.skiko.currentSystemTheme

enum class IntUiThemes {
    Light, LightWithLightHeader, Dark, SYSTEM;

    fun isDark(): Boolean = if (this == SYSTEM) {
        fromSystemTheme(currentSystemTheme)
    } else {
        this
    } == Dark

    fun isLightHeader() = this == LightWithLightHeader

    companion object {

        fun fromSystemTheme(systemTheme: SystemTheme) =
            if (systemTheme == SystemTheme.LIGHT) Light else Dark
    }
}
