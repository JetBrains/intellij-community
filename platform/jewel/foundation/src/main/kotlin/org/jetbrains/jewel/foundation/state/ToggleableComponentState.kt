package org.jetbrains.jewel.foundation.state

import androidx.compose.ui.state.ToggleableState
import org.jetbrains.jewel.foundation.state.CommonStateBitMask.Indeterminate
import org.jetbrains.jewel.foundation.state.CommonStateBitMask.Selected

/** A component state that tracks a [ToggleableState] value (on, off, or indeterminate). */
public interface ToggleableComponentState {
    /** The current toggleable state of the component (on, off, or indeterminate). */
    public val toggleableState: ToggleableState

    /** Whether the component is in the [ToggleableState.On] state. */
    public val isSelected: Boolean
        get() = toggleableState == ToggleableState.On

    /** Whether the component is in either the [ToggleableState.On] or [ToggleableState.Indeterminate] state. */
    public val isSelectedOrIndeterminate: Boolean
        get() = toggleableState != ToggleableState.Off

    /** Provides the [readToggleableState] helper for decoding a [ToggleableState] from a [ULong] bit mask. */
    public companion object {
        /** Decodes the [ToggleableState] from this [ULong] bit mask using the [Selected] and [Indeterminate] bits. */
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
