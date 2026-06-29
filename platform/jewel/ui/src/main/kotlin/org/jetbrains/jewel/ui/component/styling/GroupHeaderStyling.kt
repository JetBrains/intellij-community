package org.jetbrains.jewel.ui.component.styling

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import org.jetbrains.jewel.foundation.GenerateDataFunctions

/** Combines [GroupHeaderColors] and [GroupHeaderMetrics] to style the GroupHeader component. */
@Immutable
@GenerateDataFunctions
public class GroupHeaderStyle(
    /** The color tokens for the GroupHeader component. */
    public val colors: GroupHeaderColors,
    /** The size and spacing metrics for the GroupHeader component. */
    public val metrics: GroupHeaderMetrics,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as GroupHeaderStyle

        if (colors != other.colors) return false
        if (metrics != other.metrics) return false

        return true
    }

    override fun hashCode(): Int {
        var result = colors.hashCode()
        result = 31 * result + metrics.hashCode()
        return result
    }

    override fun toString(): String = "GroupHeaderStyle(colors=$colors, metrics=$metrics)"

    /** Companion object for [GroupHeaderStyle]. */
    public companion object
}

/** Holds color tokens for the GroupHeader component, including the divider color. */
@Immutable
@GenerateDataFunctions
public class GroupHeaderColors(
    /** The color of the divider line. */
    public val divider: Color
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as GroupHeaderColors

        return divider == other.divider
    }

    override fun hashCode(): Int = divider.hashCode()

    override fun toString(): String = "GroupHeaderColors(divider=$divider)"

    /** Companion object for [GroupHeaderColors]. */
    public companion object
}

/** Holds size and spacing metrics for the GroupHeader component, such as divider thickness and indent. */
@Immutable
@GenerateDataFunctions
public class GroupHeaderMetrics(
    /** The thickness of the divider line. */
    public val dividerThickness: Dp,
    /** The horizontal indent of the header content. */
    public val indent: Dp,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as GroupHeaderMetrics

        if (dividerThickness != other.dividerThickness) return false
        if (indent != other.indent) return false

        return true
    }

    override fun hashCode(): Int {
        var result = dividerThickness.hashCode()
        result = 31 * result + indent.hashCode()
        return result
    }

    override fun toString(): String = "GroupHeaderMetrics(dividerThickness=$dividerThickness, indent=$indent)"

    /** Companion object for [GroupHeaderMetrics]. */
    public companion object
}

/** CompositionLocal providing the current [GroupHeaderStyle]. */
public val LocalGroupHeaderStyle: ProvidableCompositionLocal<GroupHeaderStyle> = staticCompositionLocalOf {
    error("No GroupHeaderStyle provided. Have you forgotten the theme?")
}
