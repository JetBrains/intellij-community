package org.jetbrains.jewel.ui.component.styling

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import org.jetbrains.jewel.foundation.GenerateDataFunctions

/** Combines [ButtonStyle], [SplitButtonColors], and [SplitButtonMetrics] to style a split button component. */
@Stable
@GenerateDataFunctions
public class SplitButtonStyle(
    /** The style applied to the underlying button portion of the split button. */
    public val button: ButtonStyle,
    /** The colors used for the split button divider and chevron. */
    public val colors: SplitButtonColors,
    /** The size and spacing metrics for the split button divider. */
    public val metrics: SplitButtonMetrics,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SplitButtonStyle

        if (button != other.button) return false
        if (colors != other.colors) return false
        if (metrics != other.metrics) return false

        return true
    }

    override fun hashCode(): Int {
        var result = button.hashCode()
        result = 31 * result + colors.hashCode()
        result = 31 * result + metrics.hashCode()
        return result
    }

    override fun toString(): String = "SplitButtonStyle(button=$button, colors=$colors, metrics=$metrics)"

    /** Companion object for [SplitButtonStyle]. */
    public companion object
}

/** Holds color tokens for the split button divider and chevron in their various states. */
@Immutable
@GenerateDataFunctions
public class SplitButtonColors(
    /** The color of the divider between the button and the chevron. */
    public val dividerColor: Color,
    /** The color of the divider when the split button is disabled. */
    public val dividerDisabledColor: Color,
    /** The color of the chevron icon. */
    public val chevronColor: Color,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SplitButtonColors

        if (dividerColor != other.dividerColor) return false
        if (dividerDisabledColor != other.dividerDisabledColor) return false
        if (chevronColor != other.chevronColor) return false

        return true
    }

    override fun hashCode(): Int {
        var result = dividerColor.hashCode()
        result = 31 * result + dividerDisabledColor.hashCode()
        result = 31 * result + chevronColor.hashCode()
        return result
    }

    override fun toString(): String {
        return "SplitButtonColors(" +
            "dividerColor=$dividerColor, " +
            "dividerDisabledColor=$dividerDisabledColor, " +
            "chevronColor=$chevronColor" +
            ")"
    }

    /** Companion object for [SplitButtonColors]. */
    public companion object
}

/** Holds size and spacing metrics for the split button divider. */
@Stable
@GenerateDataFunctions
public class SplitButtonMetrics(
    /** The size and thickness metrics for the divider. */
    public val dividerMetrics: DividerMetrics,
    /** The padding applied around the divider. */
    public val dividerPadding: Dp,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SplitButtonMetrics

        if (dividerMetrics != other.dividerMetrics) return false
        if (dividerPadding != other.dividerPadding) return false

        return true
    }

    override fun hashCode(): Int {
        var result = dividerMetrics.hashCode()
        result = 31 * result + dividerPadding.hashCode()
        return result
    }

    override fun toString(): String =
        "SplitButtonMetrics(dividerMetrics=$dividerMetrics, dividerPadding=$dividerPadding)"

    /** Companion object for [SplitButtonMetrics]. */
    public companion object
}

/** CompositionLocal providing the default [SplitButtonStyle] for split buttons. */
public val LocalDefaultSplitButtonStyle: ProvidableCompositionLocal<SplitButtonStyle> = staticCompositionLocalOf {
    error("No default SplitButtonStyle provided. Have you forgotten the theme?")
}

/** CompositionLocal providing the outlined [SplitButtonStyle] for split buttons. */
public val LocalOutlinedSplitButtonStyle: ProvidableCompositionLocal<SplitButtonStyle> = staticCompositionLocalOf {
    error("No outlined SplitButtonStyle provided. Have you forgotten the theme?")
}
