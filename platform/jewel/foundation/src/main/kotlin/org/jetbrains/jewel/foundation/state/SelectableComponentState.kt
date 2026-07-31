package org.jetbrains.jewel.foundation.state

/** An [InteractiveComponentState] that also tracks whether the component is selected. */
public interface SelectableComponentState : InteractiveComponentState {
    /** Whether the component is currently selected. */
    public val isSelected: Boolean
}
