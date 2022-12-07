package org.jetbrains.jewel.components.state

data class TextFieldState(
    val enabled: Boolean = true,
    val hovered: Boolean = false,
    val focused: Boolean = false
) {

    companion object {

        val Default = TextFieldState()
    }
}
