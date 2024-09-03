package org.jetbrains.jewel.ui.component.styling

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.GenerateDataFunctions

@Immutable
@GenerateDataFunctions
public class DividerStyle(public val color: Color, public val metrics: DividerMetrics) {
    public companion object
}

@Immutable
@GenerateDataFunctions
public class DividerMetrics(public val thickness: Dp, public val startIndent: Dp) {
    public companion object {
        public fun defaults(thickness: Dp = 1.dp, startIndent: Dp = 0.dp): DividerMetrics =
            DividerMetrics(thickness, startIndent)
    }
}

public val LocalDividerStyle: ProvidableCompositionLocal<DividerStyle> = staticCompositionLocalOf {
    error("No DividerStyle provided. Have you forgotten the theme?")
}
