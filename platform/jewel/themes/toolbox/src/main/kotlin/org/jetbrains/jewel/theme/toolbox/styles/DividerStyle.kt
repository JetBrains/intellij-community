package org.jetbrains.jewel.theme.toolbox.styles

import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.styles.Styles
import org.jetbrains.jewel.theme.toolbox.Palette
import java.awt.SystemColor

@Immutable
data class DividerStyle(
    val appearance: DividerAppearance = DividerAppearance(),
)

data class DividerAppearance(
    val color: Color = Color(SystemColor.controlShadow.rgb),
    val stroke: BorderStroke = BorderStroke(1.dp, Color.Black),
)

val LocalDividerStyle = compositionLocalOf { DividerStyle() }
val Styles.divider: DividerStyle
    @Composable
    @ReadOnlyComposable
    get() = LocalDividerStyle.current

fun DividerStyle(palette: Palette): DividerStyle = DividerStyle(
    appearance = DividerAppearance(color = palette.dimmed)
)
