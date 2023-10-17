package org.jetbrains.jewel.intui.window.styling

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.window.styling.DecoratedWindowColors
import org.jetbrains.jewel.window.styling.DecoratedWindowMetrics
import org.jetbrains.jewel.window.styling.DecoratedWindowStyle

@Stable
@Immutable
class IntUiDecoratedWindowStyle(
    override val colors: IntUiDecoratedWindowColors,
    override val metrics: IntUiDecoratedWindowMetrics,
) : DecoratedWindowStyle {

    override fun hashCode(): Int {
        var result = colors.hashCode()
        result = 31 * result + metrics.hashCode()
        return result
    }

    override fun toString(): String = "IntUiDecoratedWindowStyle(colors=$colors, metrics=$metrics)"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is IntUiDecoratedWindowStyle) return false

        if (colors != other.colors) return false
        if (metrics != other.metrics) return false

        return true
    }

    companion object {

        @Composable fun light(
            colors: IntUiDecoratedWindowColors = IntUiDecoratedWindowColors.light(),
            metrics: IntUiDecoratedWindowMetrics = IntUiDecoratedWindowMetrics(),
        ): IntUiDecoratedWindowStyle = IntUiDecoratedWindowStyle(colors, metrics)

        @Composable fun dark(
            colors: IntUiDecoratedWindowColors = IntUiDecoratedWindowColors.dark(),
            metrics: IntUiDecoratedWindowMetrics = IntUiDecoratedWindowMetrics(),
        ): IntUiDecoratedWindowStyle = IntUiDecoratedWindowStyle(colors, metrics)
    }
}

@Stable
@Immutable
class IntUiDecoratedWindowColors(
    override val border: Color,
    override val borderInactive: Color,
) : DecoratedWindowColors {

    override fun hashCode(): Int {
        var result = border.hashCode()
        result = 31 * result + borderInactive.hashCode()
        return result
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is IntUiDecoratedWindowColors) return false

        if (border != other.border) return false
        if (borderInactive != other.borderInactive) return false

        return true
    }

    override fun toString(): String = "IntUiDecoratedWindowColors(border=$border, borderInactive=$borderInactive)"

    companion object {

        @Composable
        fun light(
            // from Window.undecorated.border
            borderColor: Color = Color(0xFF5A5D6B),
            inactiveBorderColor: Color = borderColor,
        ) = IntUiDecoratedWindowColors(
            borderColor,
            inactiveBorderColor,
        )

        @Composable
        fun dark(
            // from Window.undecorated.border
            borderColor: Color = Color(0xFF5A5D63),
            inactiveBorderColor: Color = borderColor,
        ) = IntUiDecoratedWindowColors(
            borderColor,
            inactiveBorderColor,
        )
    }
}

@Stable
class IntUiDecoratedWindowMetrics(
    override val borderWidth: Dp = 1.dp,
) : DecoratedWindowMetrics {

    override fun toString(): String = "IntUiDecoratedWindowMetrics(borderWidth=$borderWidth)"

    override fun hashCode(): Int = borderWidth.hashCode()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is IntUiDecoratedWindowMetrics) return false

        if (borderWidth != other.borderWidth) return false

        return true
    }
}
