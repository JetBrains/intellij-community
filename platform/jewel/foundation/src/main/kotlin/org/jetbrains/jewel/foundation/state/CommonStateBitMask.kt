package org.jetbrains.jewel.foundation.state

/** Bit mask constants for the common interactive component states shared across all Jewel components. */
public object CommonStateBitMask {
    /** Bit flag for the enabled state. */
    public val Enabled: ULong = 1UL shl 0

    /** Bit flag for the focused state. */
    public val Focused: ULong = 1UL shl 1

    /** Bit flag for the hovered state. */
    public val Hovered: ULong = 1UL shl 2

    /** Bit flag for the pressed state. */
    public val Pressed: ULong = 1UL shl 3

    /** Bit flag for the active state. */
    public val Active: ULong = 1UL shl 4

    /** Bit flag for the selected state. */
    public val Selected: ULong = 1UL shl 5

    /** Bit flag for the indeterminate state. */
    public val Indeterminate: ULong = 1UL shl 6

    /** The first bit offset available for component-specific state flags. */
    public const val FIRST_AVAILABLE_OFFSET: Int = 7
}
