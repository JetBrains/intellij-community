package org.jetbrains.jewel.foundation

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp

/** Global layout metrics shared across all Jewel components, defining the default outline width and row height. */
@Immutable
@GenerateDataFunctions
public class GlobalMetrics(
    /** The width of the focus outline drawn around focusable components. */
    public val outlineWidth: Dp,
    /** The default height of a single row in list-like components. */
    public val rowHeight: Dp,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as GlobalMetrics

        if (outlineWidth != other.outlineWidth) return false
        if (rowHeight != other.rowHeight) return false

        return true
    }

    override fun hashCode(): Int {
        var result = outlineWidth.hashCode()
        result = 31 * result + rowHeight.hashCode()
        return result
    }

    override fun toString(): String = "GlobalMetrics(outlineWidth=$outlineWidth, rowHeight=$rowHeight)"

    /** Companion object for [GlobalMetrics]. */
    public companion object
}

/** Composition local providing the current [GlobalMetrics] for the active theme. */
public val LocalGlobalMetrics: ProvidableCompositionLocal<GlobalMetrics> = staticCompositionLocalOf {
    error("No GlobalMetrics provided. Have you forgotten the theme?")
}
