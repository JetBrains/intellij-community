package org.jetbrains.jewel

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import org.jetbrains.jewel.foundation.Stroke
import org.jetbrains.jewel.foundation.border
import org.jetbrains.jewel.foundation.utils.thenIf

enum class Outline {
    None,
    Warning,
    Error,
    ;

    companion object {

        fun of(warning: Boolean, error: Boolean) =
            when {
                error -> Error
                warning -> Warning
                else -> None
            }
    }
}

@Composable
fun Modifier.focusOutline(
    state: FocusableComponentState,
    outlineShape: Shape = RoundedCornerShape(LocalGlobalMetrics.current.outlineCornerSize),
    outlineWidth: Dp = LocalGlobalMetrics.current.outlineWidth,
): Modifier {
    val outlineColors = LocalGlobalColors.current.outlines

    return thenIf(state.isFocused) {
        val outlineColor = outlineColors.focused
        border(Stroke.Alignment.Outside, outlineWidth, outlineColor, outlineShape)
    }
}

@Composable
fun Modifier.outline(
    state: StateWithOutline,
    outlineShape: Shape = RoundedCornerShape(LocalGlobalMetrics.current.outlineCornerSize),
    outlineWidth: Dp = LocalGlobalMetrics.current.outlineWidth,
): Modifier {
    val outlineColors = LocalGlobalColors.current.outlines

    return thenIf(state.hasOutline) {
        val outlineColor = when {
            state.isError -> outlineColors.focusedError
            state.isWarning -> outlineColors.focusedWarning
            state.isFocused -> outlineColors.focused
            else -> error("State $state says it has an outline, but doesn't really")
        }
        border(Stroke.Alignment.Inside, outlineWidth, outlineColor, outlineShape)
    }
}

@Composable
fun Modifier.outline(
    state: FocusableComponentState,
    outline: Outline,
    alignment: Stroke.Alignment = Stroke.Alignment.Outside,
    outlineShape: Shape = RoundedCornerShape(LocalGlobalMetrics.current.outlineCornerSize),
    outlineWidth: Dp = LocalGlobalMetrics.current.outlineWidth,
): Modifier {
    val outlineColors = LocalGlobalColors.current.outlines

    return thenIf(outline != Outline.None) {
        val outlineColor = when {
            state.isFocused && outline == Outline.Error -> outlineColors.focusedError
            outline == Outline.Error -> outlineColors.error
            state.isFocused && outline == Outline.Warning -> outlineColors.focusedWarning
            outline == Outline.Warning -> outlineColors.warning
            else -> error("State $state says it has an outline, but doesn't really")
        }
        border(alignment, outlineWidth, outlineColor, outlineShape)
    }
}
