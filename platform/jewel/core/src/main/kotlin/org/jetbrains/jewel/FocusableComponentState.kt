package org.jetbrains.jewel

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable

interface FocusableComponentState : InteractiveComponentState {

    @Stable
    val isFocused: Boolean

    @Composable
    fun <T> chooseValue(
        normal: T,
        disabled: T,
        focused: T,
        pressed: T,
        hovered: T,
        active: T,
    ): T =
        when {
            !isEnabled -> disabled
            isPressed && !JewelTheme.isSwingCompatMode -> pressed
            isHovered && !JewelTheme.isSwingCompatMode -> hovered
            isFocused -> focused
            isActive -> active
            else -> normal
        }
}
