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

@Stable
@GenerateDataFunctions
public class TooltipStyle(public val colors: TooltipColors, public val metrics: TooltipMetrics) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TooltipStyle

        if (colors != other.colors) return false
        if (metrics != other.metrics) return false

        return true
    }

    override fun hashCode(): Int {
        var result = colors.hashCode()
        result = 31 * result + metrics.hashCode()
        return result
    }

    override fun toString(): String = "TooltipStyle(colors=$colors, metrics=$metrics)"

    public companion object
}

@Stable
@GenerateDataFunctions
public class TooltipColors(
    public val background: Color,
    public val content: Color,
    public val border: Color,
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

    public companion object
}

@Stable
@GenerateDataFunctions
public class TooltipMetrics(
    public val contentPadding: PaddingValues,
    public val showDelay: Duration,
    public val cornerSize: CornerSize,
    public val borderWidth: Dp,
    public val shadowSize: Dp,
    public val placement: TooltipPlacement,
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

        return true
    }

    override fun hashCode(): Int {
        var result = contentPadding.hashCode()
        result = 31 * result + showDelay.hashCode()
        result = 31 * result + cornerSize.hashCode()
        result = 31 * result + borderWidth.hashCode()
        result = 31 * result + shadowSize.hashCode()
        result = 31 * result + placement.hashCode()
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
            ")"
    }

    public companion object {
        public fun defaults(
            contentPadding: PaddingValues = PaddingValues(vertical = 9.dp, horizontal = 12.dp),
            showDelay: Duration = 500.milliseconds, // ide.tooltip.initialReshowDelay
            cornerSize: CornerSize = CornerSize(4.dp),
            borderWidth: Dp = 1.dp,
            shadowSize: Dp = 12.dp,
            placement: TooltipPlacement = FixedCursorPoint(DpOffset(4.dp, 24.dp)),
        ): TooltipMetrics = TooltipMetrics(contentPadding, showDelay, cornerSize, borderWidth, shadowSize, placement)
    }
}

public val LocalTooltipStyle: ProvidableCompositionLocal<TooltipStyle> = staticCompositionLocalOf {
    error("No TooltipStyle provided. Have you forgotten the theme?")
}
