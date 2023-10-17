package org.jetbrains.jewel

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
interface GlobalColors {

    val borders: BorderColors

    val outlines: OutlineColors

    val infoContent: Color

    val paneBackground: Color
}

@Immutable
interface BorderColors {

    val normal: Color

    val focused: Color

    val disabled: Color
}

@Immutable
interface OutlineColors {

    val focused: Color

    val focusedWarning: Color

    val focusedError: Color

    val warning: Color

    val error: Color
}

val LocalGlobalColors = staticCompositionLocalOf<GlobalColors> {
    error("No GlobalColors provided")
}
