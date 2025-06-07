package org.jetbrains.jewel.foundation.state

import androidx.compose.runtime.Composable
import org.jetbrains.jewel.foundation.theme.JewelTheme

public interface FocusableComponentState : InteractiveComponentState {
    public val isFocused: Boolean

    @Composable
    public fun <T> chooseValue(normal: T, disabled: T, focused: T, pressed: T, hovered: T, active: T): T =
        when {
            !isEnabled -> disabled
            isPressed && !JewelTheme.isSwingCompatMode -> pressed
            isHovered && isFocused && !JewelTheme.isSwingCompatMode -> hovered
            isHovered && !isFocused && !JewelTheme.isSwingCompatMode -> active
            isFocused -> focused
            isActive -> active
            else -> normal
        }
}
