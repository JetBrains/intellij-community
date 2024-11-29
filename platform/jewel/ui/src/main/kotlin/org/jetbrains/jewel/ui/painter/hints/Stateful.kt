package org.jetbrains.jewel.ui.painter.hints

import androidx.compose.runtime.Immutable
import org.jetbrains.jewel.foundation.GenerateDataFunctions
import org.jetbrains.jewel.foundation.state.FocusableComponentState
import org.jetbrains.jewel.foundation.state.InteractiveComponentState
import org.jetbrains.jewel.ui.painter.PainterHint
import org.jetbrains.jewel.ui.painter.PainterProviderScope
import org.jetbrains.jewel.ui.painter.PainterSuffixHint

@Immutable
@GenerateDataFunctions
private class StatefulImpl(private val state: InteractiveComponentState) : PainterSuffixHint() {
    override fun PainterProviderScope.suffix(): String = buildString {
        if (state.isEnabled) {
            when {
                state is FocusableComponentState && state.isFocused -> append("Focused")
                state.isPressed -> append("Pressed")
                state.isHovered -> append("Hovered")
            }
        } else {
            append("Disabled")
        }
    }
}

/**
 * Selects a stateful variant of an image, based on the current [state]. Stateful variants are `Focused`, `Pressed`,
 * `Hovered`, and `Disabled`. If an image does not have the required stateful variant, the base one will be used.
 *
 * If the [state] is a [FocusableComponentState] and its [`isFocused`][FocusableComponentState.isFocused] property is
 * true, then the `Focused` variant will be used.
 *
 * For the base image name `myIcon.svg`, for example:
 *
 * | State            | Stateful image names |
 * |------------------|----------------------|
 * | Disabled         | `myIconDisabled.svg` |
 * | Enabled, focused | `myIconFocused.svg`  |
 * | Enabled, pressed | `myIconPressed.svg`  |
 * | Enabled, hovered | `myIconHovered.svg`  |
 * | Enabled, at rest | `myIcon.svg`         |
 *
 * Note that the [Swing Compat mode][org.jetbrains.jewel.foundation.theme.JewelTheme.isSwingCompatMode] value might
 * prevent the selection of the pressed and hovered states, when true.
 */
@Suppress("FunctionName") public fun Stateful(state: InteractiveComponentState): PainterHint = StatefulImpl(state)
