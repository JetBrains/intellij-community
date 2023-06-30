package org.jetbrains.jewel

internal object CommonStateBitMask {
    val Enabled = 1UL shl 0
    val Focused = 1UL shl 1
    val Hovered = 1UL shl 2
    val Pressed = 1UL shl 3
    val Error = 1UL shl 4
    val Warning = 1UL shl 5

    const val FIRST_AVAILABLE_OFFSET = 6
}
