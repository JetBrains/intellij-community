package org.jetbrains.jewel.foundation

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp

@Immutable
@GenerateDataFunctions
public class GlobalMetrics(public val outlineWidth: Dp, public val rowHeight: Dp) {
    public companion object
}

public val LocalGlobalMetrics: ProvidableCompositionLocal<GlobalMetrics> = staticCompositionLocalOf {
    error("No GlobalMetrics provided. Have you forgotten the theme?")
}
