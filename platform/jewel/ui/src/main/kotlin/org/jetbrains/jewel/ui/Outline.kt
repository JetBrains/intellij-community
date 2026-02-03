package org.jetbrains.jewel.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import org.jetbrains.jewel.foundation.Stroke
import org.jetbrains.jewel.foundation.modifier.border
import org.jetbrains.jewel.foundation.modifier.thenIf
import org.jetbrains.jewel.foundation.state.FocusableComponentState
import org.jetbrains.jewel.foundation.theme.JewelTheme

/**
 * Represents the outline state of a component, which can be `None`, `Warning`, or `Error`. Outlines are used to
 * indicate the component's state, for example, to highlight a validation error.
 */
public enum class Outline {
    /** No outline will be drawn. */
    None,

    /** A warning outline will be drawn. */
    Warning,

    /** An error outline will be drawn. */
    Error;

    public companion object {
        /**
         * Creates an [Outline] based on the provided boolean flags.
         *
         * @param warning If true, a [Warning] outline will be created.
         * @param error If true, an [Error] outline will be created. Takes precedence over [warning].
         * @return An [Outline] that is either [Error] (if [error] is true), [Warning] (if [warning] is true), or
         *   [None].
         */
        public fun of(warning: Boolean, error: Boolean): Outline =
            when {
                error -> Error
                warning -> Warning
                else -> None
            }
    }
}

/**
 * Adds a focus outline to a composable. The outline is only shown when the component is focused.
 *
 * @param state The [FocusableComponentState] to observe for focus changes.
 * @param outlineShape The [Shape] to use for the outline.
 * @param alignment The [Stroke.Alignment] to use for the outline.
 * @param outlineWidth The width of the outline [Dp].
 * @param expand The amount of space to expand the outline by.
 */
@Composable
public fun Modifier.focusOutline(
    state: FocusableComponentState,
    outlineShape: Shape,
    alignment: Stroke.Alignment = Stroke.Alignment.Outside,
    outlineWidth: Dp = JewelTheme.globalMetrics.outlineWidth,
    expand: Dp = Dp.Unspecified,
): Modifier = focusOutline(state.isFocused, outlineShape, alignment, outlineWidth, expand)

/**
 * Adds a focus outline to a composable. The outline is only shown when the component is focused.
 *
 * @param showOutline Whether to show the outline.
 * @param outlineShape The [Shape] to use for the outline.
 * @param alignment The [Stroke.Alignment] to use for the outline.
 * @param outlineWidth The width of the outline [Dp].
 * @param expand The amount of space to expand the outline by.
 */
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

/**
 * Adds an outline to a composable based on the provided [Outline] state. The outline color depends on whether the
 * component is focused.
 *
 * @param outline The [Outline] state to use.
 * @param outlineShape The [Shape] to use for the outline.
 * @param alignment The [Stroke.Alignment] to use for the outline.
 * @param outlineWidth The width of the outline [Dp].
 * @param focused Whether the component is focused.
 */
@Composable
public fun Modifier.outline(
    outline: Outline,
    focused: Boolean,
    outlineShape: Shape = RectangleShape,
    alignment: Stroke.Alignment = Stroke.Alignment.Outside,
    outlineWidth: Dp = JewelTheme.globalMetrics.outlineWidth,
): Modifier {
    val outlineColors = JewelTheme.globalColors.outlines

    return thenIf(outline != Outline.None) {
        val outlineColor =
            when (outline) {
                Outline.Error -> if (focused) outlineColors.focusedError else outlineColors.error
                Outline.Warning -> if (focused) outlineColors.focusedWarning else outlineColors.warning
                else -> return@thenIf Modifier
            }
        border(alignment, outlineWidth, outlineColor, outlineShape)
    }
}

/**
 * Adds an outline to a composable based on the provided [Outline] state, observing the focus state from
 * [FocusableComponentState].
 *
 * @param state The [FocusableComponentState] to observe for focus changes.
 * @param outline The [Outline] state to use.
 * @param outlineShape The [Shape] to use for the outline.
 * @param alignment The [Stroke.Alignment] to use for the outline.
 * @param outlineWidth The width of the outline [Dp].
 */
@Composable
public fun Modifier.outline(
    state: FocusableComponentState,
    outline: Outline,
    outlineShape: Shape,
    alignment: Stroke.Alignment = Stroke.Alignment.Outside,
    outlineWidth: Dp = JewelTheme.globalMetrics.outlineWidth,
): Modifier = outline(outline, state.isFocused, outlineShape, alignment, outlineWidth)
