package org.jetbrains.jewel.components.state

import androidx.compose.ui.state.ToggleableState

data class CheckboxState(
    val toggle: ToggleableState,
    val mouse: ButtonMouseState = ButtonMouseState.None,
    val enabled: Boolean = true,
    val focused: Boolean = false
)
