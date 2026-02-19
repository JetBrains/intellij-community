package org.jetbrains.jewel.ui.component.styling

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import kotlin.time.Duration
import org.jetbrains.jewel.foundation.GenerateDataFunctions

@Immutable
@GenerateDataFunctions
public class HorizontalProgressBarStyle(
    public val colors: HorizontalProgressBarColors,
    public val metrics: HorizontalProgressBarMetrics,
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

    public companion object
}

@Immutable
@GenerateDataFunctions
public class HorizontalProgressBarColors(
    public val track: Color,
    public val progress: Color,
    public val indeterminateBase: Color,
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

    public companion object
}

@Immutable
@GenerateDataFunctions
public class HorizontalProgressBarMetrics(
    public val cornerSize: CornerSize,
    public val minHeight: Dp,
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

    public companion object
}

public val LocalHorizontalProgressBarStyle: ProvidableCompositionLocal<HorizontalProgressBarStyle> =
    staticCompositionLocalOf<HorizontalProgressBarStyle> {
        error("No HorizontalProgressBarStyle provided. Have you forgotten the theme?")
    }
