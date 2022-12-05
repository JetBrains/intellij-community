package org.jetbrains.jewel.styles

import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.IntelliJMetrics
import org.jetbrains.jewel.IntelliJPalette

@Immutable
data class SeparatorStyle(
    val appearance: SeparatorAppearance = SeparatorAppearance(),
)

data class SeparatorAppearance(
    val background: Color = Color.Unspecified,
    val stroke: BorderStroke = BorderStroke(1.dp, Color(0xFFD1D1D1)),
)

val LocalSeparatorStyle = compositionLocalOf { SeparatorStyle() }
val Styles.separator: SeparatorStyle
    @Composable
    @ReadOnlyComposable
    get() = LocalSeparatorStyle.current

fun SeparatorStyle(palette: IntelliJPalette, metrics: IntelliJMetrics): SeparatorStyle = SeparatorStyle(
    appearance = SeparatorAppearance(
        background = palette.separator.background,
        stroke = BorderStroke(metrics.separator.strokeWidth, palette.separator.color)
    )
)
