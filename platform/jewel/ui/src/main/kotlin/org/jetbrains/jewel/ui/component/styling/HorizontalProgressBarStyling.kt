package org.jetbrains.jewel.ui.component.styling

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import kotlin.time.Duration
import org.jetbrains.jewel.foundation.GenerateDataFunctions

/** Combines colors, metrics, and indeterminate cycle duration for styling a horizontal progress bar. */
@Immutable
@GenerateDataFunctions
public class HorizontalProgressBarStyle(
    /** The color tokens for the progress bar. */
    public val colors: HorizontalProgressBarColors,
    /** The size and spacing metrics for the progress bar. */
    public val metrics: HorizontalProgressBarMetrics,
    /** The duration of one full indeterminate animation cycle. */
    public val indeterminateCycleDuration: Duration,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as HorizontalProgressBarStyle

        if (colors != other.colors) return false
        if (metrics != other.metrics) return false
        if (indeterminateCycleDuration != other.indeterminateCycleDuration) return false

        return true
    }

    override fun hashCode(): Int {
        var result = colors.hashCode()
        result = 31 * result + metrics.hashCode()
        result = 31 * result + indeterminateCycleDuration.hashCode()
        return result
    }

    override fun toString(): String {
        return "HorizontalProgressBarStyle(" +
            "colors=$colors, " +
            "metrics=$metrics, " +
            "indeterminateCycleDuration=$indeterminateCycleDuration" +
            ")"
    }

    /** Companion object for [HorizontalProgressBarStyle]. */
    public companion object
}

/**
 * Holds color tokens for the horizontal progress bar component in its various states, including indeterminate
 * animation.
 */
@Immutable
@GenerateDataFunctions
public class HorizontalProgressBarColors(
    /** The color of the track (unfilled portion) of the progress bar. */
    public val track: Color,
    /** The color of the filled progress indicator. */
    public val progress: Color,
    /** The base color used during indeterminate animation. */
    public val indeterminateBase: Color,
    /** The highlight color used during indeterminate animation. */
    public val indeterminateHighlight: Color,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as HorizontalProgressBarColors

        if (track != other.track) return false
        if (progress != other.progress) return false
        if (indeterminateBase != other.indeterminateBase) return false
        if (indeterminateHighlight != other.indeterminateHighlight) return false

        return true
    }

    override fun hashCode(): Int {
        var result = track.hashCode()
        result = 31 * result + progress.hashCode()
        result = 31 * result + indeterminateBase.hashCode()
        result = 31 * result + indeterminateHighlight.hashCode()
        return result
    }

    override fun toString(): String {
        return "HorizontalProgressBarColors(" +
            "track=$track, " +
            "progress=$progress, " +
            "indeterminateBase=$indeterminateBase, " +
            "indeterminateHighlight=$indeterminateHighlight" +
            ")"
    }

    /** Companion object for [HorizontalProgressBarColors]. */
    public companion object
}

/**
 * Holds size and spacing metrics for the horizontal progress bar component, including corner size and indeterminate
 * highlight width.
 */
@Immutable
@GenerateDataFunctions
public class HorizontalProgressBarMetrics(
    /** The corner radius of the progress bar track and indicator. */
    public val cornerSize: CornerSize,
    /** The minimum height of the progress bar. */
    public val minHeight: Dp,
    /** The width of the moving highlight segment during indeterminate animation. */
    public val indeterminateHighlightWidth: Dp,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as HorizontalProgressBarMetrics

        if (cornerSize != other.cornerSize) return false
        if (minHeight != other.minHeight) return false
        if (indeterminateHighlightWidth != other.indeterminateHighlightWidth) return false

        return true
    }

    override fun hashCode(): Int {
        var result = cornerSize.hashCode()
        result = 31 * result + minHeight.hashCode()
        result = 31 * result + indeterminateHighlightWidth.hashCode()
        return result
    }

    override fun toString(): String {
        return "HorizontalProgressBarMetrics(" +
            "cornerSize=$cornerSize, " +
            "minHeight=$minHeight, " +
            "indeterminateHighlightWidth=$indeterminateHighlightWidth" +
            ")"
    }

    /** Companion object for [HorizontalProgressBarMetrics]. */
    public companion object
}

/** CompositionLocal providing the current [HorizontalProgressBarStyle]. */
public val LocalHorizontalProgressBarStyle: ProvidableCompositionLocal<HorizontalProgressBarStyle> =
    staticCompositionLocalOf<HorizontalProgressBarStyle> {
        error("No HorizontalProgressBarStyle provided. Have you forgotten the theme?")
    }
