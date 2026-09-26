package org.jetbrains.jewel.ui.component.styling

import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import org.jetbrains.jewel.foundation.GenerateDataFunctions
import org.jetbrains.jewel.ui.component.FixedCursorPoint

/** Combines [TooltipColors] and [TooltipMetrics] to fully style a tooltip component. */
@Stable
@GenerateDataFunctions
public class TooltipStyle(
    /** The color tokens for the tooltip. */
    public val colors: TooltipColors,
    /** The size and spacing metrics for the tooltip. */
    public val metrics: TooltipMetrics,
    /** Controls how long the tooltip remains visible before automatically hiding. */
    public val autoHideBehavior: TooltipAutoHideBehavior,
) {
    public constructor(
        colors: TooltipColors,
        metrics: TooltipMetrics,
    ) : this(colors, metrics, TooltipAutoHideBehavior.Normal)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TooltipStyle

        if (colors != other.colors) return false
        if (metrics != other.metrics) return false
        if (autoHideBehavior != other.autoHideBehavior) return false

        return true
    }

    override fun hashCode(): Int {
        var result = colors.hashCode()
        result = 31 * result + metrics.hashCode()
        result = 31 * result + autoHideBehavior.hashCode()
        return result
    }

    override fun toString(): String =
        "TooltipStyle(colors=$colors, metrics=$metrics, autoHideBehavior=$autoHideBehavior)"

    /** Companion object for [TooltipStyle]. */
    public companion object
}

/** Holds color tokens for a tooltip component, covering background, content, border, and shadow. */
@Stable
@GenerateDataFunctions
public class TooltipColors(
    /** The background color of the tooltip. */
    public val background: Color,
    /** The content (text) color of the tooltip. */
    public val content: Color,
    /** The border color of the tooltip. */
    public val border: Color,
    /** The shadow color of the tooltip. */
    public val shadow: Color,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TooltipColors

        if (background != other.background) return false
        if (content != other.content) return false
        if (border != other.border) return false
        if (shadow != other.shadow) return false

        return true
    }

    override fun hashCode(): Int {
        var result = background.hashCode()
        result = 31 * result + content.hashCode()
        result = 31 * result + border.hashCode()
        result = 31 * result + shadow.hashCode()
        return result
    }

    override fun toString(): String {
        return "TooltipColors(" +
            "background=$background, " +
            "content=$content, " +
            "border=$border, " +
            "shadow=$shadow" +
            ")"
    }

    /** Companion object for [TooltipColors]. */
    public companion object
}

/**
 * Holds size and spacing metrics for a tooltip component, including padding, delays, corner size, border, shadow, and
 * placement.
 */
@Stable
@GenerateDataFunctions
public class TooltipMetrics(
    /** The inner padding applied to the tooltip content. */
    public val contentPadding: PaddingValues,
    /** The delay before the tooltip appears after the cursor hovers over the target. */
    public val showDelay: Duration,
    /** The corner radius of the tooltip shape. */
    public val cornerSize: CornerSize,
    /** The width of the tooltip border. */
    public val borderWidth: Dp,
    /** The size of the drop shadow around the tooltip. */
    public val shadowSize: Dp,
    /** The placement policy for positioning the tooltip relative to the cursor. */
    public val placement: TooltipPlacement,
    /** The delay after which a regular tooltip automatically disappears. */
    public val regularDisappearDelay: Duration,
    /** The delay after which a full (rich) tooltip automatically disappears. */
    public val fullDisappearDelay: Duration,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TooltipMetrics

        if (contentPadding != other.contentPadding) return false
        if (showDelay != other.showDelay) return false
        if (cornerSize != other.cornerSize) return false
        if (borderWidth != other.borderWidth) return false
        if (shadowSize != other.shadowSize) return false
        if (placement != other.placement) return false
        if (regularDisappearDelay != other.regularDisappearDelay) return false
        if (fullDisappearDelay != other.fullDisappearDelay) return false

        return true
    }

    override fun hashCode(): Int {
        var result = contentPadding.hashCode()
        result = 31 * result + showDelay.hashCode()
        result = 31 * result + cornerSize.hashCode()
        result = 31 * result + borderWidth.hashCode()
        result = 31 * result + shadowSize.hashCode()
        result = 31 * result + placement.hashCode()
        result = 31 * result + regularDisappearDelay.hashCode()
        result = 31 * result + fullDisappearDelay.hashCode()
        return result
    }

    override fun toString(): String {
        return "TooltipMetrics(" +
            "contentPadding=$contentPadding, " +
            "showDelay=$showDelay, " +
            "cornerSize=$cornerSize, " +
            "borderWidth=$borderWidth, " +
            "shadowSize=$shadowSize, " +
            "placement=$placement" +
            "regularDisappearDelay=$regularDisappearDelay, " +
            "fullDisappearDelay=$fullDisappearDelay" +
            ")"
    }

    /** Companion object for [TooltipMetrics]. */
    public companion object {
        /**
         * Creates default [TooltipMetrics] with standard Int UI values and configurable disappear delays.
         *
         * @param contentPadding Inner padding of the tooltip content.
         * @param showDelay Delay before the tooltip appears after the cursor hovers.
         * @param regularDisappearDelay Delay after which a regular tooltip disappears automatically.
         * @param fullDisappearDelay Delay after which a full (rich) tooltip disappears automatically.
         * @param cornerSize Corner radius of the tooltip shape.
         * @param borderWidth Width of the tooltip border.
         * @param shadowSize Size of the drop shadow.
         * @param placement Policy for positioning the tooltip relative to the cursor.
         */
        public fun defaults(
            contentPadding: PaddingValues = PaddingValues(vertical = 9.dp, horizontal = 12.dp),
            showDelay: Duration = 500.milliseconds, // ide.tooltip.initialReshowDelay
            regularDisappearDelay: Duration = 10000.milliseconds, // ide.helptooltip.regular.dismissDelay
            fullDisappearDelay: Duration = 30000.milliseconds, // ide.helptooltip.full.dismissDelay
            cornerSize: CornerSize = CornerSize(4.dp),
            borderWidth: Dp = 1.dp,
            shadowSize: Dp = 12.dp,
            placement: TooltipPlacement = FixedCursorPoint(DpOffset(4.dp, 24.dp)),
        ): TooltipMetrics =
            TooltipMetrics(
                contentPadding,
                showDelay,
                cornerSize,
                borderWidth,
                shadowSize,
                placement,
                regularDisappearDelay,
                fullDisappearDelay,
            )
    }
}

/** Controls how long a tooltip remains visible before automatically hiding. */
public enum class TooltipAutoHideBehavior {
    /** The tooltip never hides automatically. */
    Never,
    /** The tooltip hides after the regular disappear delay. */
    Normal,
    /** The tooltip hides after the full (longer) disappear delay. */
    Long,
}

/** CompositionLocal that provides the current [TooltipStyle] for tooltip components. */
public val LocalTooltipStyle: ProvidableCompositionLocal<TooltipStyle> = staticCompositionLocalOf {
    error("No TooltipStyle provided. Have you forgotten the theme?")
}
