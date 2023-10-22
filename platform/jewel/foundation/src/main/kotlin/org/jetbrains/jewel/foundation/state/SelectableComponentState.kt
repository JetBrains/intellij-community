package org.jetbrains.jewel.foundation.state

import androidx.compose.runtime.Stable

interface SelectableComponentState : FocusableComponentState {

    @Stable
    val isSelected: Boolean
}
