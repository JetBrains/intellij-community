package org.jetbrains.jewel.foundation.state

import androidx.compose.ui.state.ToggleableState
import org.jetbrains.jewel.foundation.state.CommonStateBitMask.Indeterminate
import org.jetbrains.jewel.foundation.state.CommonStateBitMask.Selected

public interface ToggleableComponentState {
    public val toggleableState: ToggleableState

    public val isSelected: Boolean
        get() = toggleableState == ToggleableState.On

    public val isSelectedOrIndeterminate: Boolean
        get() = toggleableState != ToggleableState.Off

    public companion object {
        public fun ULong.readToggleableState(): ToggleableState {
            val selected = this and Selected != 0UL
            val indeterminate = this and Indeterminate != 0UL

            return when {
                indeterminate -> ToggleableState.Indeterminate
                selected -> ToggleableState.On
                else -> ToggleableState.Off
            }
        }
    }
}
