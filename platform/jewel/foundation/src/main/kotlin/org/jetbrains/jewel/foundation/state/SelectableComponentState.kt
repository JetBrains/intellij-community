package org.jetbrains.jewel.foundation.state

import androidx.compose.runtime.Stable

interface SelectableComponentState : InteractiveComponentState {

    @Stable
    val isSelected: Boolean
}
