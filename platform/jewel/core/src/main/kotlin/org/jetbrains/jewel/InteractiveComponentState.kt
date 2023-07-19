package org.jetbrains.jewel

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable

interface InteractiveComponentState {

    @Stable
    val isFocused: Boolean

    @Stable
    val isEnabled: Boolean

    @Stable
    val isHovered: Boolean

    @Stable
    val isPressed: Boolean

    @Composable
    fun <T> chooseValue(
        normal: T,
        disabled: T,
        focused: T,
        pressed: T,
        hovered: T,
    ): T =
        when {
            !isEnabled -> disabled
            isPressed && !IntelliJTheme.isSwingCompatMode -> pressed
            isHovered && !IntelliJTheme.isSwingCompatMode -> hovered
            isFocused -> focused
            else -> normal
        }
}
