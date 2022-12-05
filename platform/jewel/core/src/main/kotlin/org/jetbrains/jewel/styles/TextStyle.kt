package org.jetbrains.jewel.styles

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.text.TextStyle

val LocalTextStyle = compositionLocalOf<TextStyle> { localNotProvided() }
val Styles.text: TextStyle
    @Composable
    @ReadOnlyComposable
    get() = LocalTextStyle.current

@Composable
fun Styles.withTextStyle(textStyle: TextStyle, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalTextStyle provides textStyle, content = content)
}
