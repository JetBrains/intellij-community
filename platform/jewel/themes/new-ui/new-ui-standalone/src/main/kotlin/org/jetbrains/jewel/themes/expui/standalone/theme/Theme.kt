package org.jetbrains.jewel.themes.expui.standalone.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.compositionLocalOf

val LocalIsDarkTheme = compositionLocalOf { false }

interface Theme {

    val isDark: Boolean

    @Composable
    fun provide(content: @Composable () -> Unit) {
        CompositionLocalProvider(
            *provideValues(),
            content = content,
        )
    }

    fun provideValues(): Array<ProvidedValue<*>>
}
