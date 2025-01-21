package org.jetbrains.jewel.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import org.jetbrains.jewel.foundation.Stroke
import org.jetbrains.jewel.foundation.modifier.border
import org.jetbrains.jewel.foundation.modifier.thenIf
import org.jetbrains.jewel.foundation.state.FocusableComponentState
import org.jetbrains.jewel.foundation.theme.JewelTheme

public enum class Outline {
    None,
    Warning,
    Error;

    public companion object {
        public fun of(warning: Boolean, error: Boolean): Outline =
            when {
                error -> Error
                warning -> Warning
                else -> None
            }
    }
}

@Composable
public fun Modifier.focusOutline(
    state: FocusableComponentState,
    outlineShape: Shape,
    alignment: Stroke.Alignment = Stroke.Alignment.Outside,
    outlineWidth: Dp = JewelTheme.globalMetrics.outlineWidth,
    expand: Dp = Dp.Unspecified,
): Modifier = focusOutline(state.isFocused, outlineShape, alignment, outlineWidth, expand)

@Composable
public fun Modifier.focusOutline(
    showOutline: Boolean,
    outlineShape: Shape,
    alignment: Stroke.Alignment = Stroke.Alignment.Outside,
    outlineWidth: Dp = JewelTheme.globalMetrics.outlineWidth,
    expand: Dp = Dp.Unspecified,
): Modifier {
    val outlineColors = JewelTheme.globalColors.outlines

    return thenIf(showOutline) {
        val outlineColor = outlineColors.focused
        border(alignment, outlineWidth, outlineColor, outlineShape, expand)
    }
}

@Composable
public fun Modifier.outline(
    state: FocusableComponentState,
    outline: Outline,
    outlineShape: Shape,
    alignment: Stroke.Alignment = Stroke.Alignment.Outside,
    outlineWidth: Dp = JewelTheme.globalMetrics.outlineWidth,
): Modifier {
    val outlineColors = JewelTheme.globalColors.outlines

    return thenIf(outline != Outline.None) {
        val outlineColor =
            when {
                state.isFocused && outline == Outline.Error -> outlineColors.focusedError
                outline == Outline.Error -> outlineColors.error
                state.isFocused && outline == Outline.Warning -> outlineColors.focusedWarning
                outline == Outline.Warning -> outlineColors.warning
                else -> error("State $state says it has an outline, but doesn't really")
            }
        border(alignment, outlineWidth, outlineColor, outlineShape)
    }
}
