package org.jetbrains.jewel.foundation

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
@GenerateDataFunctions
public class GlobalColors(
    public val borders: BorderColors,
    public val outlines: OutlineColors,
    public val infoContent: Color,
    public val paneBackground: Color,
) {

    public companion object
}

@Immutable
@GenerateDataFunctions
public class BorderColors(
    public val normal: Color,
    public val focused: Color,
    public val disabled: Color,
) {

    public companion object
}

@Immutable
@GenerateDataFunctions
public class OutlineColors(
    public val focused: Color,
    public val focusedWarning: Color,
    public val focusedError: Color,
    public val warning: Color,
    public val error: Color,
) {

    public companion object
}

public val LocalGlobalColors: ProvidableCompositionLocal<GlobalColors> =
    staticCompositionLocalOf {
        error("No GlobalColors provided. Have you forgotten the theme?")
    }
