package org.jetbrains.jewel.window.styling

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import org.jetbrains.jewel.foundation.GenerateDataFunctions
import org.jetbrains.jewel.window.DecoratedWindowState

@Immutable
@GenerateDataFunctions
public class DecoratedWindowStyle(
    public val colors: DecoratedWindowColors,
    public val metrics: DecoratedWindowMetrics,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DecoratedWindowStyle

        if (colors != other.colors) return false
        if (metrics != other.metrics) return false

        return true
    }

    override fun hashCode(): Int {
        var result = colors.hashCode()
        result = 31 * result + metrics.hashCode()
        return result
    }

    override fun toString(): String = "DecoratedWindowStyle(colors=$colors, metrics=$metrics)"

    public companion object
}

@Immutable
@GenerateDataFunctions
public class DecoratedWindowColors(public val border: Color, public val borderInactive: Color) {
    @Composable
    public fun borderFor(state: DecoratedWindowState): State<Color> =
        rememberUpdatedState(
            when {
                !state.isActive -> borderInactive
                else -> border
            }
        )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DecoratedWindowColors

        if (border != other.border) return false
        if (borderInactive != other.borderInactive) return false

        return true
    }

    override fun hashCode(): Int {
        var result = border.hashCode()
        result = 31 * result + borderInactive.hashCode()
        return result
    }

    override fun toString(): String = "DecoratedWindowColors(border=$border, borderInactive=$borderInactive)"

    public companion object
}

@Immutable
@GenerateDataFunctions
public class DecoratedWindowMetrics(public val borderWidth: Dp) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DecoratedWindowMetrics

        return borderWidth == other.borderWidth
    }

    override fun hashCode(): Int = borderWidth.hashCode()

    override fun toString(): String = "DecoratedWindowMetrics(borderWidth=$borderWidth)"

    public companion object
}

public val LocalDecoratedWindowStyle: ProvidableCompositionLocal<DecoratedWindowStyle> = staticCompositionLocalOf {
    error("No DecoratedWindowStyle provided. Have you forgotten the theme?")
}
