package org.jetbrains.jewel.ui.component.styling

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import org.jetbrains.jewel.foundation.GenerateDataFunctions
import kotlin.time.Duration

@Stable
@GenerateDataFunctions
public class ScrollbarStyle(
    public val colors: ScrollbarColors,
    public val metrics: ScrollbarMetrics,
    public val trackClickBehavior: TrackClickBehavior,
    public val scrollbarVisibility: ScrollbarVisibility,
) {
    public companion object
}

@Immutable
@GenerateDataFunctions
public class ScrollbarColors(
    public val thumbBackground: Color,
    public val thumbBackgroundHovered: Color,
    public val thumbBackgroundPressed: Color,
    public val thumbBorder: Color,
    public val thumbBorderHovered: Color,
    public val thumbBorderPressed: Color,
    public val trackBackground: Color,
    public val trackBackgroundHovered: Color,
) {
    public companion object
}

@Stable
@GenerateDataFunctions
public class ScrollbarMetrics(
    public val thumbCornerSize: CornerSize,
    public val thumbThickness: Dp,
    public val thumbThicknessExpanded: Dp,
    public val minThumbLength: Dp,
    public val trackPadding: PaddingValues,
    public val trackPaddingExpanded: PaddingValues,
) {
    public companion object
}

public sealed interface ScrollbarVisibility {
    public data object AlwaysVisible : ScrollbarVisibility

    @GenerateDataFunctions
    public class WhenScrolling(
        public val appearAnimationDuration: Duration,
        public val disappearAnimationDuration: Duration,
        public val expandAnimationDuration: Duration,
        public val lingerDuration: Duration,
    ) : ScrollbarVisibility {
        public companion object
    }
}

public enum class TrackClickBehavior {
    NextPage,
    JumpToSpot,
}

public val LocalScrollbarStyle: ProvidableCompositionLocal<ScrollbarStyle> =
    staticCompositionLocalOf {
        error("No ScrollbarStyle provided. Have you forgotten the theme?")
    }
