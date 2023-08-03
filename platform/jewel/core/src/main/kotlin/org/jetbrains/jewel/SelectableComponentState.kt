package org.jetbrains.jewel

import androidx.compose.runtime.Stable

interface SelectableComponentState : FocusableComponentState {

    @Stable
    val isSelected: Boolean
}
