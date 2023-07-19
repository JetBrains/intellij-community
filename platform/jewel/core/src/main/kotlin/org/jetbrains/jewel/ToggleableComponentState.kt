package org.jetbrains.jewel

import androidx.compose.runtime.Stable
import androidx.compose.ui.state.ToggleableState
import org.jetbrains.jewel.CommonStateBitMask.Indeterminate
import org.jetbrains.jewel.CommonStateBitMask.Selected

interface ToggleableComponentState : SelectableComponentState {

    @Stable
    val toggleableState: ToggleableState

    companion object {

        fun ULong.readToggleableState(): ToggleableState {
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
