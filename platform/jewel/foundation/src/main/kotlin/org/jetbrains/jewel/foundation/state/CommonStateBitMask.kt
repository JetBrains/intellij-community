package org.jetbrains.jewel.foundation.state

public object CommonStateBitMask {
    public val Enabled: ULong = 1UL shl 0
    public val Focused: ULong = 1UL shl 1
    public val Hovered: ULong = 1UL shl 2
    public val Pressed: ULong = 1UL shl 3
    public val Active: ULong = 1UL shl 4
    public val Selected: ULong = 1UL shl 5
    public val Indeterminate: ULong = 1UL shl 6

    public const val FIRST_AVAILABLE_OFFSET: Int = 7
}
