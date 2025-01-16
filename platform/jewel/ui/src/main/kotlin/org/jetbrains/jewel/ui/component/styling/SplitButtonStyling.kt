package org.jetbrains.jewel.ui.component.styling

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import org.jetbrains.jewel.foundation.GenerateDataFunctions

@Stable
@GenerateDataFunctions
public class SplitButtonStyle(
    public val button: ButtonStyle,
    public val colors: SplitButtonColors,
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

    public companion object
}

@Immutable
@GenerateDataFunctions
public class SplitButtonColors(
    public val dividerColor: Color,
    public val dividerDisabledColor: Color,
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

    public companion object
}

@Stable
@GenerateDataFunctions
public class SplitButtonMetrics(public val dividerMetrics: DividerMetrics, public val dividerPadding: Dp) {
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

    public companion object
}

public val LocalDefaultSplitButtonStyle: ProvidableCompositionLocal<SplitButtonStyle> = staticCompositionLocalOf {
    error("No default SplitButtonStyle provided. Have you forgotten the theme?")
}

public val LocalOutlinedSplitButtonStyle: ProvidableCompositionLocal<SplitButtonStyle> = staticCompositionLocalOf {
    error("No outlined SplitButtonStyle provided. Have you forgotten the theme?")
}
