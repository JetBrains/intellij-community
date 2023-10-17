package org.jetbrains.jewel.intui.window

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import org.jetbrains.jewel.intui.core.IntUiThemeDefinition
import org.jetbrains.jewel.intui.window.styling.IntUiDecoratedWindowStyle
import org.jetbrains.jewel.intui.window.styling.IntUiTitleBarStyle
import org.jetbrains.jewel.window.styling.LocalDecoratedWindowStyle
import org.jetbrains.jewel.window.styling.LocalTitleBarStyle
import org.jetbrains.jewel.window.styling.TitleBarStyle

@Composable
fun IntUiThemeDefinition.decoratedWindowComponentStyling(
    windowStyle: IntUiDecoratedWindowStyle = if (isDark) {
        IntUiDecoratedWindowStyle.dark()
    } else {
        IntUiDecoratedWindowStyle.light()
    },
    titleBarStyle: TitleBarStyle = if (isDark) {
        IntUiTitleBarStyle.dark()
    } else {
        IntUiTitleBarStyle.light()
    },
): Array<ProvidedValue<*>> = arrayOf(
    LocalDecoratedWindowStyle provides windowStyle,
    LocalTitleBarStyle provides titleBarStyle,
)
