package org.jetbrains.jewel.intui.window

import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.window.styling.dark
import org.jetbrains.jewel.intui.window.styling.light
import org.jetbrains.jewel.ui.ComponentStyling
import org.jetbrains.jewel.window.styling.DecoratedWindowStyle
import org.jetbrains.jewel.window.styling.LocalDecoratedWindowStyle
import org.jetbrains.jewel.window.styling.LocalTitleBarStyle
import org.jetbrains.jewel.window.styling.TitleBarStyle

/**
 * Provides [DecoratedWindowStyle] and [TitleBarStyle] into this [ComponentStyling], defaulting to the Int UI light or
 * dark variants based on [JewelTheme.isDark].
 *
 * @param windowStyle The [DecoratedWindowStyle] to use. Defaults to [DecoratedWindowStyle.dark] or
 *   [DecoratedWindowStyle.light] based on the current theme.
 * @param titleBarStyle The [TitleBarStyle] to use. Defaults to [TitleBarStyle.dark] or [TitleBarStyle.light] based on
 *   the current theme.
 */
public fun ComponentStyling.decoratedWindow(
    windowStyle: DecoratedWindowStyle? = null,
    titleBarStyle: TitleBarStyle? = null,
): ComponentStyling = provide {
    val isDark = JewelTheme.isDark

    val currentWindowStyle = windowStyle ?: if (isDark) DecoratedWindowStyle.dark() else DecoratedWindowStyle.light()
    val currentTitleBarStyle = titleBarStyle ?: if (isDark) TitleBarStyle.dark() else TitleBarStyle.light()

    arrayOf(LocalDecoratedWindowStyle provides currentWindowStyle, LocalTitleBarStyle provides currentTitleBarStyle)
}
