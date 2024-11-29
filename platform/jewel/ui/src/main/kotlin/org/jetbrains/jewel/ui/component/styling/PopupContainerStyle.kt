package org.jetbrains.jewel.ui.component.styling

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import org.jetbrains.jewel.foundation.GenerateDataFunctions

@Stable
@GenerateDataFunctions
public class PopupContainerStyle(
    public val isDark: Boolean,
    public val colors: PopupContainerColors,
    public val metrics: PopupContainerMetrics,
) {
    public companion object
}

@Immutable
@GenerateDataFunctions
public class PopupContainerColors(public val background: Color, public val border: Color, public val shadow: Color) {
    public companion object
}

@Stable
@GenerateDataFunctions
public class PopupContainerMetrics(
    public val cornerSize: CornerSize,
    public val menuMargin: PaddingValues,
    public val contentPadding: PaddingValues,
    public val offset: DpOffset,
    public val shadowSize: Dp,
    public val borderWidth: Dp,
) {
    public companion object
}

public val LocalPopupContainerStyle: ProvidableCompositionLocal<PopupContainerStyle> = staticCompositionLocalOf {
    error("No PopupContainerStyle provided. Have you forgotten the theme?")
}
