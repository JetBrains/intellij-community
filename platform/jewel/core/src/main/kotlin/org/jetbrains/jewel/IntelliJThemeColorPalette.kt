package org.jetbrains.jewel

import androidx.compose.ui.graphics.Color

interface IntelliJThemeColorPalette {

    fun lookup(colorKey: String): Color?
}

object EmptyThemeColorPalette : IntelliJThemeColorPalette {

    override fun lookup(colorKey: String): Color? = null
}
