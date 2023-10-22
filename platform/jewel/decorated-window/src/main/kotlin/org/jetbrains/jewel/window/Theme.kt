package org.jetbrains.jewel.window

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.window.styling.DecoratedWindowStyle
import org.jetbrains.jewel.window.styling.LocalDecoratedWindowStyle
import org.jetbrains.jewel.window.styling.LocalTitleBarStyle
import org.jetbrains.jewel.window.styling.TitleBarStyle

val JewelTheme.Companion.defaultTitleBarStyle: TitleBarStyle
    @Composable
    @ReadOnlyComposable
    get() = LocalTitleBarStyle.current

val JewelTheme.Companion.defaultDecoratedWindowStyle: DecoratedWindowStyle
    @Composable
    @ReadOnlyComposable
    get() = LocalDecoratedWindowStyle.current
