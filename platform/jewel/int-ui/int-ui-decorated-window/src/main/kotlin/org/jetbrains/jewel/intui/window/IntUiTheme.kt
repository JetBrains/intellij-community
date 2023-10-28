package org.jetbrains.jewel.intui.window

import androidx.compose.runtime.Composable
import org.jetbrains.jewel.intui.window.styling.dark
import org.jetbrains.jewel.intui.window.styling.light
import org.jetbrains.jewel.ui.theme.ComponentStyleProviderScope
import org.jetbrains.jewel.window.styling.DecoratedWindowStyle
import org.jetbrains.jewel.window.styling.LocalDecoratedWindowStyle
import org.jetbrains.jewel.window.styling.LocalTitleBarStyle
import org.jetbrains.jewel.window.styling.TitleBarStyle

@Composable
fun ComponentStyleProviderScope.provideDecoratedWindowComponentStyling(
    windowStyle: DecoratedWindowStyle = if (theme.isDark) {
        DecoratedWindowStyle.dark()
    } else {
        DecoratedWindowStyle.light()
    },
    titleBarStyle: TitleBarStyle = if (theme.isDark) {
        TitleBarStyle.dark()
    } else {
        TitleBarStyle.light()
    },
) {
    provide(
        LocalDecoratedWindowStyle provides windowStyle,
        LocalTitleBarStyle provides titleBarStyle,
    )
}
