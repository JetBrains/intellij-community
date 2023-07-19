package org.jetbrains.jewel

import androidx.compose.runtime.Stable

interface SelectableComponentState : InteractiveComponentState {

    @Stable
    val isSelected: Boolean
}
