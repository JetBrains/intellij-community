package org.jetbrains.jewel.ui.component.styling

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import org.jetbrains.jewel.foundation.GenerateDataFunctions

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
    public val thumbBackgroundActive: Color,
    public val thumbOpaqueBackground: Color,
    public val thumbOpaqueBackgroundHovered: Color,
    public val thumbBorder: Color,
    public val thumbBorderActive: Color,
    public val thumbOpaqueBorder: Color,
    public val thumbOpaqueBorderHovered: Color,
    public val trackBackground: Color,
    public val trackBackgroundExpanded: Color,
    public val trackOpaqueBackground: Color,
    public val trackOpaqueBackgroundHovered: Color,
) {
    public companion object
}

@Stable
@GenerateDataFunctions
public class ScrollbarMetrics(public val thumbCornerSize: CornerSize, public val minThumbLength: Dp) {
    public companion object
}

public sealed interface ScrollbarVisibility {
    public val trackThickness: Dp
    public val trackThicknessExpanded: Dp
    public val trackPadding: PaddingValues
    public val trackPaddingWithBorder: PaddingValues
    public val trackColorAnimationDuration: Duration
    public val expandAnimationDuration: Duration
    public val thumbColorAnimationDuration: Duration
    public val lingerDuration: Duration

    @GenerateDataFunctions
    public class AlwaysVisible(
        public override val trackThickness: Dp,
        public override val trackPadding: PaddingValues,
        public override val trackPaddingWithBorder: PaddingValues,
        public override val thumbColorAnimationDuration: Duration,
        public override val trackColorAnimationDuration: Duration,
        public val scrollbarBackgroundColorLight: Color,
        public val scrollbarBackgroundColorDark: Color,
    ) : ScrollbarVisibility {
        public override val trackThicknessExpanded: Dp = trackThickness
        public override val expandAnimationDuration: Duration = 0.milliseconds
        public override val lingerDuration: Duration = 0.milliseconds

        public companion object
    }

    @GenerateDataFunctions
    public class WhenScrolling(
        public override val trackThickness: Dp,
        public override val trackThicknessExpanded: Dp,
        public override val trackPadding: PaddingValues,
        public override val trackPaddingWithBorder: PaddingValues,
        public override val trackColorAnimationDuration: Duration,
        public override val expandAnimationDuration: Duration,
        public override val thumbColorAnimationDuration: Duration,
        public override val lingerDuration: Duration,
    ) : ScrollbarVisibility {

        public companion object
    }
}

public enum class TrackClickBehavior {
    NextPage,
    JumpToSpot,
}

public val LocalScrollbarStyle: ProvidableCompositionLocal<ScrollbarStyle> = staticCompositionLocalOf {
    error("No ScrollbarStyle provided. Have you forgotten the theme?")
}
