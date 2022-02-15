package org.jetbrains.jewel.theme.intellij.styles

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import org.jetbrains.jewel.styles.ControlStyle
import org.jetbrains.jewel.styles.Styles
import org.jetbrains.jewel.styles.localNotProvided
import org.jetbrains.jewel.theme.intellij.IntelliJPalette

typealias FrameStyle = ControlStyle<FrameAppearance, Unit>

@Immutable
data class FrameAppearance(
    val backgroundColor: Color = Color.White,
)

val LocalFrameStyle = compositionLocalOf<FrameStyle> { localNotProvided() }
val Styles.frame: FrameStyle
    @Composable
    @ReadOnlyComposable
    get() = LocalFrameStyle.current

fun FrameStyle(palette: IntelliJPalette) = FrameStyle {
    default {
        state(Unit, FrameAppearance(backgroundColor = palette.background))
    }
}
