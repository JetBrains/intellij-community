package org.jetbrains.jewel.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import org.jetbrains.jewel.foundation.Stroke
import org.jetbrains.jewel.foundation.modifier.border
import org.jetbrains.jewel.foundation.state.FocusableComponentState
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.util.thenIf

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
    outlineShape: Shape,
    alignment: Stroke.Alignment = Stroke.Alignment.Outside,
    outlineWidth: Dp = JewelTheme.globalMetrics.outlineWidth,
): Modifier {
    val outlineColors = JewelTheme.globalColors.outlines

    return thenIf(state.isFocused) {
        val outlineColor = outlineColors.focused
        border(alignment, outlineWidth, outlineColor, outlineShape)
    }
}

@Composable
fun Modifier.outline(
    state: FocusableComponentState,
    outline: Outline,
    outlineShape: Shape,
    alignment: Stroke.Alignment = Stroke.Alignment.Outside,
    outlineWidth: Dp = JewelTheme.globalMetrics.outlineWidth,
): Modifier {
    val outlineColors = JewelTheme.globalColors.outlines

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
