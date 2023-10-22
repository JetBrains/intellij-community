package org.jetbrains.jewel.foundation

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
@GenerateDataFunctions
class GlobalColors(
    val borders: BorderColors,
    val outlines: OutlineColors,
    val infoContent: Color,
    val paneBackground: Color,
) {

    companion object
}

@Immutable
@GenerateDataFunctions
class BorderColors(
    val normal: Color,
    val focused: Color,
    val disabled: Color,
) {

    companion object
}

@Immutable
@GenerateDataFunctions
class OutlineColors(
    val focused: Color,
    val focusedWarning: Color,
    val focusedError: Color,
    val warning: Color,
    val error: Color,
) {

    companion object
}

val LocalGlobalColors = staticCompositionLocalOf<GlobalColors> {
    error("No GlobalColors provided")
}
