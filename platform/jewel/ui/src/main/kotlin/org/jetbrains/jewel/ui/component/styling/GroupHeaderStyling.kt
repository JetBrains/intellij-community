package org.jetbrains.jewel.ui.component.styling

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import org.jetbrains.jewel.foundation.GenerateDataFunctions

@Immutable
@GenerateDataFunctions
public class GroupHeaderStyle(public val colors: GroupHeaderColors, public val metrics: GroupHeaderMetrics) {
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

    public companion object
}

@Immutable
@GenerateDataFunctions
public class GroupHeaderColors(public val divider: Color) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as GroupHeaderColors

        return divider == other.divider
    }

    override fun hashCode(): Int = divider.hashCode()

    override fun toString(): String = "GroupHeaderColors(divider=$divider)"

    public companion object
}

@Immutable
@GenerateDataFunctions
public class GroupHeaderMetrics(public val dividerThickness: Dp, public val indent: Dp) {
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

    public companion object
}

public val LocalGroupHeaderStyle: ProvidableCompositionLocal<GroupHeaderStyle> = staticCompositionLocalOf {
    error("No GroupHeaderStyle provided. Have you forgotten the theme?")
}
