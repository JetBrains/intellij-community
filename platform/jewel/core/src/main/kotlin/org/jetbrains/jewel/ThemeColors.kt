package org.jetbrains.jewel

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
interface ThemeColors {

    val borders: BorderColors
    val outlines: OutlineColors

    @SwingLafKey("*.infoForeground")
    val infoContent: Color
}

@Immutable
interface BorderColors {

    @SwingLafKey("Component.borderColor")
    val normal: Color

    @SwingLafKey("Component.focusedBorderColor")
    val focused: Color

    @SwingLafKey("*.disabledBorderColor")
    val disabled: Color
}

@Immutable
interface OutlineColors {

    @SwingLafKey("*.focusColor")
    val focused: Color

    @SwingLafKey("Component.warningFocusColor")
    val focusedWarning: Color

    @SwingLafKey("Component.errorFocusColor")
    val focusedError: Color

    @SwingLafKey("Component.inactiveWarningFocusColor")
    val warning: Color

    @SwingLafKey("Component.inactiveErrorFocusColor")
    val error: Color
}

val LocalThemeColors = staticCompositionLocalOf<ThemeColors> {
    error("No ThemeColors provided")
}
