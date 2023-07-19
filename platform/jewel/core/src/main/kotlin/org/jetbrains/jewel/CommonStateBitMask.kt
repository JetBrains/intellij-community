package org.jetbrains.jewel

internal object CommonStateBitMask {
    val Enabled = 1UL shl 0
    val Focused = 1UL shl 1
    val Hovered = 1UL shl 2
    val Pressed = 1UL shl 3

    @Deprecated("Shouldn't be part of the core state")
    val Error = 1UL shl 4

    @Deprecated("Shouldn't be part of the core state")
    val Warning = 1UL shl 5
    val Selected = 1UL shl 6
    val Indeterminate = 1UL shl 7

    const val FIRST_AVAILABLE_OFFSET = 8
}
