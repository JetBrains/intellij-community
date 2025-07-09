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
import org.jetbrains.jewel.ui.theme.scrollbarStyle

/**
 * Defines the visual style for a scrollbar. This includes colors, metrics, and behaviors.
 *
 * The style can be accessed through [`JewelTheme.scrollbarStyle`][scrollbarStyle].
 *
 * @param colors The set of colors used to paint the scrollbar.
 * @param metrics The sizing and shape properties of the scrollbar.
 * @param trackClickBehavior The behavior of the scrollbar track when clicked.
 * @param scrollbarVisibility The visibility behavior of the scrollbar.
 */
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

/**
 * Contains the color properties for a [ScrollbarStyle].
 *
 * @param thumbBackground The background color of the scrollbar thumb.
 * @param thumbBackgroundActive The background color of the scrollbar thumb when active (e.g., being dragged).
 * @param thumbOpaqueBackground The background color of the scrollbar thumb when the scrollbar is in opaque mode.
 * @param thumbOpaqueBackgroundHovered The background color of the scrollbar thumb when hovered and in opaque mode.
 * @param thumbBorder The border color of the scrollbar thumb.
 * @param thumbBorderActive The border color of the scrollbar thumb when active.
 * @param thumbOpaqueBorder The border color of the scrollbar thumb when in opaque mode.
 * @param thumbOpaqueBorderHovered The border color of the scrollbar thumb when hovered and in opaque mode.
 * @param trackBackground The background color of the scrollbar track.
 * @param trackBackgroundExpanded The background color of the scrollbar track when expanded.
 * @param trackOpaqueBackground The background color of the scrollbar track when in opaque mode.
 * @param trackOpaqueBackgroundHovered The background color of the scrollbar track when hovered and in opaque mode.
 */
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

    override fun toString(): String =
        "ScrollbarColors(" +
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

    public companion object
}

/**
 * Defines the metrics for a [ScrollbarStyle].
 *
 * @param thumbCornerSize The corner size of the scrollbar thumb.
 * @param minThumbLength The minimum length of the scrollbar thumb.
 */
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

/** Defines the visibility behavior of a scrollbar. */
public sealed interface ScrollbarVisibility {
    /** The thickness of the scrollbar track when not expanded. */
    public val trackThickness: Dp

    /** The thickness of the scrollbar track when expanded (e.g., on hover). */
    public val trackThicknessExpanded: Dp

    /** The padding around the scrollbar track. */
    public val trackPadding: PaddingValues

    /** The padding around the scrollbar track when a border is visible. */
    public val trackPaddingWithBorder: PaddingValues

    /** The duration for the track color animation. */
    public val trackColorAnimationDuration: Duration

    /** The duration for the track expansion animation. */
    public val expandAnimationDuration: Duration

    /** The duration for the thumb color animation. */
    public val thumbColorAnimationDuration: Duration

    /**
     * The duration to wait before hiding the scrollbar after scrolling has stopped. When using a
     * [org.jetbrains.jewel.ui.component.VerticallyScrollableContainer] or a
     * [org.jetbrains.jewel.ui.component.HorizontallyScrollableContainer], the linger starts after the scroll ends and
     * the user stops moving the mouse over the content.
     */
    public val lingerDuration: Duration

    /**
     * A [ScrollbarVisibility] that keeps the scrollbar always visible.
     *
     * This is equivalent to the _Always_ setting in macOS' _System Settings > Appearance > Show scroll bars_ settings.
     * It is also used when the _Automatically based on mouse or trackpad_ option is selected, and there is a mouse
     * connected to the computer.
     *
     * Note that this behavior is usually only used on macOS, and it causes the scrollbars to be laid out to the side of
     * a [org.jetbrains.jewel.ui.component.VerticallyScrollableContainer]'s main content (or below, for a
     * [org.jetbrains.jewel.ui.component.HorizontallyScrollableContainer]), instead of overlaid.
     *
     * @param trackThickness The thickness of the scrollbar track.
     * @param trackPadding The padding around the scrollbar track.
     * @param trackPaddingWithBorder The padding when a border is visible.
     * @param thumbColorAnimationDuration The duration for the thumb color animation.
     * @param trackColorAnimationDuration The duration for the track color animation.
     * @param scrollbarBackgroundColorLight The background color in light theme.
     * @param scrollbarBackgroundColorDark The background color in dark theme.
     */
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

    /**
     * A [ScrollbarVisibility] that shows the scrollbar only when scrolling, overlaid on top of the content.
     *
     * This is the only available behavior on Windows and Linux. On macOS, it is equivalent to the _When scrolling_
     * setting in macOS' _System Settings > Appearance > Show scroll bars_ settings. It is also used when the
     * _Automatically based on mouse or trackpad_ option is selected, and there is no mouse connected to the computer.
     *
     * @param trackThickness The thickness of the scrollbar track.
     * @param trackThicknessExpanded The thickness when expanded.
     * @param trackPadding The padding around the scrollbar track.
     * @param trackPaddingWithBorder The padding when a border is visible.
     * @param trackColorAnimationDuration The duration for the track color animation.
     * @param expandAnimationDuration The duration for the track expansion animation.
     * @param thumbColorAnimationDuration The duration for the thumb color animation.
     * @param lingerDuration The duration to wait before hiding the scrollbar.
     */
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

/** Defines the behavior when the scrollbar track is clicked. */
public enum class TrackClickBehavior {
    /**
     * Scrolls to the next page in the direction of the click.
     *
     * Equivalent to the _Jump to the next page_ setting in macOS' _System Settings > Appearance > Click in the scroll
     * bar to_ settings.
     *
     * This is normally not used on Windows and Linux.
     */
    NextPage,

    /**
     * Jumps directly to the clicked spot on the track.
     *
     * Equivalent to the _Jump to the spot that's clicked_ setting in macOS' _System Settings > Appearance > Click in
     * the scroll bar to_ settings.
     *
     * This is the only available behavior on Windows and Linux.
     */
    JumpToSpot,
}

/** The CompositionLocal that provides the default [ScrollbarStyle]. */
public val LocalScrollbarStyle: ProvidableCompositionLocal<ScrollbarStyle> = staticCompositionLocalOf {
    error("No ScrollbarStyle provided. Have you forgotten the theme?")
}
