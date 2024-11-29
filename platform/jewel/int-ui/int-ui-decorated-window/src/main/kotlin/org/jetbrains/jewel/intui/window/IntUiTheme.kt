package org.jetbrains.jewel.intui.window

import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.window.styling.dark
import org.jetbrains.jewel.intui.window.styling.light
import org.jetbrains.jewel.ui.ComponentStyling
import org.jetbrains.jewel.window.styling.DecoratedWindowStyle
import org.jetbrains.jewel.window.styling.LocalDecoratedWindowStyle
import org.jetbrains.jewel.window.styling.LocalTitleBarStyle
import org.jetbrains.jewel.window.styling.TitleBarStyle

public fun ComponentStyling.decoratedWindow(
    windowStyle: DecoratedWindowStyle? = null,
    titleBarStyle: TitleBarStyle? = null,
): ComponentStyling = provide {
    val isDark = JewelTheme.isDark

    val currentWindowStyle = windowStyle ?: if (isDark) DecoratedWindowStyle.dark() else DecoratedWindowStyle.light()
    val currentTitleBarStyle = titleBarStyle ?: if (isDark) TitleBarStyle.dark() else TitleBarStyle.light()

    arrayOf(LocalDecoratedWindowStyle provides currentWindowStyle, LocalTitleBarStyle provides currentTitleBarStyle)
}
