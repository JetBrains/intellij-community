package org.jetbrains.jewel.ui.component.styling

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import org.jetbrains.jewel.foundation.GenerateDataFunctions

@Immutable
@GenerateDataFunctions
public class GroupHeaderStyle(public val colors: GroupHeaderColors, public val metrics: GroupHeaderMetrics) {
    public companion object
}

@Immutable
@GenerateDataFunctions
public class GroupHeaderColors(public val divider: Color) {
    public companion object
}

@Immutable
@GenerateDataFunctions
public class GroupHeaderMetrics(public val dividerThickness: Dp, public val indent: Dp) {
    public companion object
}

public val LocalGroupHeaderStyle: ProvidableCompositionLocal<GroupHeaderStyle> = staticCompositionLocalOf {
    error("No GroupHeaderStyle provided. Have you forgotten the theme?")
}
