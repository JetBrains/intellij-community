package org.jetbrains.jewel.intui.window

import androidx.compose.runtime.Composable
import org.jetbrains.jewel.intui.core.IntUiThemeDefinition
import org.jetbrains.jewel.intui.window.styling.IntUiDecoratedWindowStyle
import org.jetbrains.jewel.intui.window.styling.IntUiTitleBarStyle
import org.jetbrains.jewel.window.styling.DecoratedWindowStyle
import org.jetbrains.jewel.window.styling.LocalDecoratedWindowStyle
import org.jetbrains.jewel.window.styling.LocalTitleBarStyle
import org.jetbrains.jewel.window.styling.TitleBarStyle

@Composable
fun IntUiThemeDefinition.decoratedWindowStyle(): DecoratedWindowStyle =
    if (isDark) {
        IntUiDecoratedWindowStyle.dark()
    } else {
        IntUiDecoratedWindowStyle.light()
    }

@Composable
fun IntUiThemeDefinition.withDecoratedWindow(
    titleBarStyle: TitleBarStyle = if (isDark) {
        IntUiTitleBarStyle.dark()
    } else {
        IntUiTitleBarStyle.light()
    },
): IntUiThemeDefinition {
    return withExtensions(
        LocalDecoratedWindowStyle provides decoratedWindowStyle(),
        LocalTitleBarStyle provides titleBarStyle,
    )
}
