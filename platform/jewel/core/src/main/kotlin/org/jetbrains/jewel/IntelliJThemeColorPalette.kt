package org.jetbrains.jewel

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color

@Stable
interface IntelliJThemeColorPalette {

    fun lookup(colorKey: String): Color? = rawMap[colorKey]

    val rawMap: Map<String, Color>
}

@Immutable
object EmptyThemeColorPalette : IntelliJThemeColorPalette {

    override val rawMap: Map<String, Color> = emptyMap()
}
