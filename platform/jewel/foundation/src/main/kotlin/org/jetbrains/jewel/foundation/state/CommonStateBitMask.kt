package org.jetbrains.jewel.foundation.state

object CommonStateBitMask {

    val Enabled = 1UL shl 0
    val Focused = 1UL shl 1
    val Hovered = 1UL shl 2
    val Pressed = 1UL shl 3
    val Active = 1UL shl 4
    val Selected = 1UL shl 5
    val Indeterminate = 1UL shl 6

    const val FIRST_AVAILABLE_OFFSET = 7
}
