package org.jetbrains.jewel.ui.component.styling

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import org.jetbrains.jewel.foundation.GenerateDataFunctions

@Stable
@GenerateDataFunctions
public class SplitButtonStyle(
    public val button: ButtonStyle,
    public val colors: SplitButtonColors,
    public val metrics: SplitButtonMetrics,
) {
    public companion object
}

@Immutable
@GenerateDataFunctions
public class SplitButtonColors(
    public val dividerColor: Color,
    public val dividerDisabledColor: Color,
    public val chevronColor: Color,
) {
    public companion object
}

@Stable
@GenerateDataFunctions
public class SplitButtonMetrics(public val dividerMetrics: DividerMetrics, public val dividerPadding: Dp) {
    public companion object
}

public val LocalDefaultSplitButtonStyle: ProvidableCompositionLocal<SplitButtonStyle> = staticCompositionLocalOf {
    error("No default SplitButtonStyle provided. Have you forgotten the theme?")
}

public val LocalOutlinedSplitButtonStyle: ProvidableCompositionLocal<SplitButtonStyle> = staticCompositionLocalOf {
    error("No outlined SplitButtonStyle provided. Have you forgotten the theme?")
}
