package org.jetbrains.jewel.foundation.state

import androidx.compose.runtime.Composable
import org.jetbrains.jewel.foundation.theme.JewelTheme

/**
 * An [InteractiveComponentState] that also tracks focus. Provides a composable `chooseValue` helper that resolves a
 * state-dependent value based on the current enabled, pressed, hovered, focused, and active flags.
 */
public interface FocusableComponentState : InteractiveComponentState {
    /** Whether the component currently has keyboard focus. */
    public val isFocused: Boolean

    /**
     * Returns one of the provided values based on the component's current state priority: disabled → pressed → hovered
     * → focused → active → normal. Hover takes precedence over focus, so a hovered component uses the [hovered] value
     * even when it is not focused (unless Swing compat mode is on). This matches how rollover works for Swing controls
     * and menu items, which highlight on hover regardless of focus.
     *
     * @param T The type of the state-dependent value.
     * @param normal The value to use when the component is in its default, uninteracted state.
     * @param disabled The value to use when [isEnabled] is `false`.
     * @param focused The value to use when [isFocused] is `true` and the component is not hovered or pressed.
     * @param pressed The value to use when [isPressed] is `true` (and Swing compat mode is off).
     * @param hovered The value to use when [isHovered] is `true` (and Swing compat mode is off), regardless of focus.
     * @param active The value to use when [isActive] is `true`.
     */
    @Composable
    public fun <T> chooseValue(normal: T, disabled: T, focused: T, pressed: T, hovered: T, active: T): T =
        when {
            !isEnabled -> disabled
            isPressed && !JewelTheme.isSwingCompatMode -> pressed
            isHovered && !JewelTheme.isSwingCompatMode -> hovered
            isFocused -> focused
            isActive -> active
            else -> normal
        }
}
