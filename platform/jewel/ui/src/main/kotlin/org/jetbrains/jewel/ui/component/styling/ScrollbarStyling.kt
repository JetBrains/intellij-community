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
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ScrollbarStyle

        if (colors != other.colors) return false
        if (metrics != other.metrics) return false
        if (trackClickBehavior != other.trackClickBehavior) return false
        if (scrollbarVisibility != other.scrollbarVisibility) return false

        return true
    }

    override fun hashCode(): Int {
        var result = colors.hashCode()
        result = 31 * result + metrics.hashCode()
        result = 31 * result + trackClickBehavior.hashCode()
        result = 31 * result + scrollbarVisibility.hashCode()
        return result
    }

    override fun toString(): String {
        return "ScrollbarStyle(" +
            "colors=$colors, " +
            "metrics=$metrics, " +
            "trackClickBehavior=$trackClickBehavior, " +
            "scrollbarVisibility=$scrollbarVisibility" +
            ")"
    }

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
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ScrollbarColors

        if (thumbBackground != other.thumbBackground) return false
        if (thumbBackgroundActive != other.thumbBackgroundActive) return false
        if (thumbOpaqueBackground != other.thumbOpaqueBackground) return false
        if (thumbOpaqueBackgroundHovered != other.thumbOpaqueBackgroundHovered) return false
        if (thumbBorder != other.thumbBorder) return false
        if (thumbBorderActive != other.thumbBorderActive) return false
        if (thumbOpaqueBorder != other.thumbOpaqueBorder) return false
        if (thumbOpaqueBorderHovered != other.thumbOpaqueBorderHovered) return false
        if (trackBackground != other.trackBackground) return false
        if (trackBackgroundExpanded != other.trackBackgroundExpanded) return false
        if (trackOpaqueBackground != other.trackOpaqueBackground) return false
        if (trackOpaqueBackgroundHovered != other.trackOpaqueBackgroundHovered) return false

        return true
    }

    override fun hashCode(): Int {
        var result = thumbBackground.hashCode()
        result = 31 * result + thumbBackgroundActive.hashCode()
        result = 31 * result + thumbOpaqueBackground.hashCode()
        result = 31 * result + thumbOpaqueBackgroundHovered.hashCode()
        result = 31 * result + thumbBorder.hashCode()
        result = 31 * result + thumbBorderActive.hashCode()
        result = 31 * result + thumbOpaqueBorder.hashCode()
        result = 31 * result + thumbOpaqueBorderHovered.hashCode()
        result = 31 * result + trackBackground.hashCode()
        result = 31 * result + trackBackgroundExpanded.hashCode()
        result = 31 * result + trackOpaqueBackground.hashCode()
        result = 31 * result + trackOpaqueBackgroundHovered.hashCode()
        return result
    }

    override fun toString(): String {
        return "ScrollbarColors(" +
            "thumbBackground=$thumbBackground, " +
            "thumbBackgroundActive=$thumbBackgroundActive, " +
            "thumbOpaqueBackground=$thumbOpaqueBackground, " +
            "thumbOpaqueBackgroundHovered=$thumbOpaqueBackgroundHovered, " +
            "thumbBorder=$thumbBorder, " +
            "thumbBorderActive=$thumbBorderActive, " +
            "thumbOpaqueBorder=$thumbOpaqueBorder, " +
            "thumbOpaqueBorderHovered=$thumbOpaqueBorderHovered, " +
            "trackBackground=$trackBackground, " +
            "trackBackgroundExpanded=$trackBackgroundExpanded, " +
            "trackOpaqueBackground=$trackOpaqueBackground, " +
            "trackOpaqueBackgroundHovered=$trackOpaqueBackgroundHovered" +
            ")"
    }

    public companion object
}

@Stable
@GenerateDataFunctions
public class ScrollbarMetrics(public val thumbCornerSize: CornerSize, public val minThumbLength: Dp) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ScrollbarMetrics

        if (thumbCornerSize != other.thumbCornerSize) return false
        if (minThumbLength != other.minThumbLength) return false

        return true
    }

    override fun hashCode(): Int {
        var result = thumbCornerSize.hashCode()
        result = 31 * result + minThumbLength.hashCode()
        return result
    }

    override fun toString(): String =
        "ScrollbarMetrics(thumbCornerSize=$thumbCornerSize, minThumbLength=$minThumbLength)"

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

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as AlwaysVisible

            if (trackThickness != other.trackThickness) return false
            if (trackPadding != other.trackPadding) return false
            if (trackPaddingWithBorder != other.trackPaddingWithBorder) return false
            if (thumbColorAnimationDuration != other.thumbColorAnimationDuration) return false
            if (trackColorAnimationDuration != other.trackColorAnimationDuration) return false
            if (scrollbarBackgroundColorLight != other.scrollbarBackgroundColorLight) return false
            if (scrollbarBackgroundColorDark != other.scrollbarBackgroundColorDark) return false
            if (trackThicknessExpanded != other.trackThicknessExpanded) return false
            if (expandAnimationDuration != other.expandAnimationDuration) return false
            if (lingerDuration != other.lingerDuration) return false

            return true
        }

        override fun hashCode(): Int {
            var result = trackThickness.hashCode()
            result = 31 * result + trackPadding.hashCode()
            result = 31 * result + trackPaddingWithBorder.hashCode()
            result = 31 * result + thumbColorAnimationDuration.hashCode()
            result = 31 * result + trackColorAnimationDuration.hashCode()
            result = 31 * result + scrollbarBackgroundColorLight.hashCode()
            result = 31 * result + scrollbarBackgroundColorDark.hashCode()
            result = 31 * result + trackThicknessExpanded.hashCode()
            result = 31 * result + expandAnimationDuration.hashCode()
            result = 31 * result + lingerDuration.hashCode()
            return result
        }

        override fun toString(): String {
            return "AlwaysVisible(" +
                "trackThickness=$trackThickness, " +
                "trackPadding=$trackPadding, " +
                "trackPaddingWithBorder=$trackPaddingWithBorder, " +
                "thumbColorAnimationDuration=$thumbColorAnimationDuration, " +
                "trackColorAnimationDuration=$trackColorAnimationDuration, " +
                "scrollbarBackgroundColorLight=$scrollbarBackgroundColorLight, " +
                "scrollbarBackgroundColorDark=$scrollbarBackgroundColorDark, " +
                "trackThicknessExpanded=$trackThicknessExpanded, " +
                "expandAnimationDuration=$expandAnimationDuration, " +
                "lingerDuration=$lingerDuration" +
                ")"
        }

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
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as WhenScrolling

            if (trackThickness != other.trackThickness) return false
            if (trackThicknessExpanded != other.trackThicknessExpanded) return false
            if (trackPadding != other.trackPadding) return false
            if (trackPaddingWithBorder != other.trackPaddingWithBorder) return false
            if (trackColorAnimationDuration != other.trackColorAnimationDuration) return false
            if (expandAnimationDuration != other.expandAnimationDuration) return false
            if (thumbColorAnimationDuration != other.thumbColorAnimationDuration) return false
            if (lingerDuration != other.lingerDuration) return false

            return true
        }

        override fun hashCode(): Int {
            var result = trackThickness.hashCode()
            result = 31 * result + trackThicknessExpanded.hashCode()
            result = 31 * result + trackPadding.hashCode()
            result = 31 * result + trackPaddingWithBorder.hashCode()
            result = 31 * result + trackColorAnimationDuration.hashCode()
            result = 31 * result + expandAnimationDuration.hashCode()
            result = 31 * result + thumbColorAnimationDuration.hashCode()
            result = 31 * result + lingerDuration.hashCode()
            return result
        }

        override fun toString(): String {
            return "WhenScrolling(" +
                "trackThickness=$trackThickness, " +
                "trackThicknessExpanded=$trackThicknessExpanded, " +
                "trackPadding=$trackPadding, " +
                "trackPaddingWithBorder=$trackPaddingWithBorder, " +
                "trackColorAnimationDuration=$trackColorAnimationDuration, " +
                "expandAnimationDuration=$expandAnimationDuration, " +
                "thumbColorAnimationDuration=$thumbColorAnimationDuration, " +
                "lingerDuration=$lingerDuration" +
                ")"
        }

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
