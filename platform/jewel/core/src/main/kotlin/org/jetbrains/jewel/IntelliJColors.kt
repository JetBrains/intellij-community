package org.jetbrains.jewel

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.ui.graphics.Color

@Stable
class IntelliJColors(
    foreground: Color,
    background: Color,
    borderColor: Color,

    disabledForeground: Color,
    disabledBackground: Color,
    disabledBorderColor: Color
) {

    var foreground by mutableStateOf(foreground, structuralEqualityPolicy())
        internal set

    var background by mutableStateOf(background, structuralEqualityPolicy())
        internal set

    var borderColor by mutableStateOf(borderColor, structuralEqualityPolicy())
        internal set

    var disabledForeground by mutableStateOf(disabledForeground, structuralEqualityPolicy())
        internal set

    var disabledBackground by mutableStateOf(disabledBackground, structuralEqualityPolicy())
        internal set

    var disabledBorderColor by mutableStateOf(disabledBorderColor, structuralEqualityPolicy())
        internal set

    fun copy(
        foreground: Color = this.foreground,
        background: Color = this.background,
        borderColor: Color = this.borderColor,
        disabledForeground: Color = this.disabledForeground,
        disabledBackground: Color = this.disabledBackground,
        disabledBorderColor: Color = this.disabledBorderColor
    ): IntelliJColors {
        return IntelliJColors(
            foreground = foreground,
            background = background,
            borderColor = borderColor,
            disabledForeground = disabledForeground,
            disabledBackground = disabledBackground,
            disabledBorderColor = disabledBorderColor
        )
    }

    override fun toString(): String {
        return "IntellijColors(" +
            "foreground=$foreground, " +
            "background=$background, " +
            "borderColor=$borderColor, " +
            "disabledForeground=$disabledForeground, " +
            "disabledBackground=$disabledBackground, " +
            "disabledBorderColor=$disabledBorderColor" +
            ")"
    }
}

internal val LocalIntelliJColors = staticCompositionLocalOf<IntelliJColors> {
    error("No IntelliJColors provided")
}
