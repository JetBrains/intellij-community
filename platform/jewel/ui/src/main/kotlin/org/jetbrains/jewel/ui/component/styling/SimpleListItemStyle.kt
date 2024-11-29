package org.jetbrains.jewel.ui.component.styling

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import org.jetbrains.jewel.foundation.GenerateDataFunctions

@GenerateDataFunctions
public class SimpleListItemStyle(public val colors: SimpleListItemColors, public val metrics: SimpleListItemMetrics) {
    public companion object
}

@Stable
@GenerateDataFunctions
public class SimpleListItemColors(
    public val background: Color = Color.Unspecified,
    public val backgroundFocused: Color,
    public val backgroundSelected: Color,
    public val backgroundSelectedFocused: Color,
    public val content: Color,
    public val contentFocused: Color,
    public val contentSelected: Color,
    public val contentSelectedFocused: Color,
) {
    public companion object
}

@Stable
@GenerateDataFunctions
public class SimpleListItemMetrics(
    public val innerPadding: PaddingValues,
    public val outerPadding: PaddingValues,
    public val selectionBackgroundCornerSize: CornerSize,
) {
    public companion object
}

public val LocalSimpleListItemStyleStyle: ProvidableCompositionLocal<SimpleListItemStyle> = staticCompositionLocalOf {
    error("No LocalSimpleListItemStyleStyle provided. Have you forgotten the theme?")
}
