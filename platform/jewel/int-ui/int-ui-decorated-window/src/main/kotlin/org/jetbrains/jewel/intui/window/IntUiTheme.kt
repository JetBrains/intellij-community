package org.jetbrains.jewel.intui.window

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import org.jetbrains.jewel.foundation.theme.ThemeDefinition
import org.jetbrains.jewel.intui.window.styling.dark
import org.jetbrains.jewel.intui.window.styling.light
import org.jetbrains.jewel.window.styling.DecoratedWindowStyle
import org.jetbrains.jewel.window.styling.LocalDecoratedWindowStyle
import org.jetbrains.jewel.window.styling.LocalTitleBarStyle
import org.jetbrains.jewel.window.styling.TitleBarStyle

@Composable
fun ThemeDefinition.decoratedWindowComponentStyling(
    windowStyle: DecoratedWindowStyle = if (isDark) {
        DecoratedWindowStyle.dark()
    } else {
        DecoratedWindowStyle.light()
    },
    titleBarStyle: TitleBarStyle = if (isDark) {
        TitleBarStyle.dark()
    } else {
        TitleBarStyle.light()
    },
): Array<ProvidedValue<*>> = arrayOf(
    LocalDecoratedWindowStyle provides windowStyle,
    LocalTitleBarStyle provides titleBarStyle,
)
