package org.jetbrains.jewel

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color

@Stable
interface IntelliJThemeColorPalette {

    fun lookup(colorKey: String): Color?
}

@Immutable
object EmptyThemeColorPalette : IntelliJThemeColorPalette {

    override fun lookup(colorKey: String): Color? = null
}
