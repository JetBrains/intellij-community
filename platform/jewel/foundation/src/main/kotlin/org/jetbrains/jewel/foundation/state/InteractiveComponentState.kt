package org.jetbrains.jewel.foundation.state

/**
 * Base interface for interactive component states, tracking whether a component is active, enabled, hovered, or
 * pressed.
 */
public interface InteractiveComponentState {
    /** Whether the component is active (e.g., its parent window has focus). */
    public val isActive: Boolean

    /** Whether the component is enabled and can receive user interaction. */
    public val isEnabled: Boolean

    /** Whether the pointer is currently hovering over the component. */
    public val isHovered: Boolean

    /** Whether the component is currently being pressed. */
    public val isPressed: Boolean
}
