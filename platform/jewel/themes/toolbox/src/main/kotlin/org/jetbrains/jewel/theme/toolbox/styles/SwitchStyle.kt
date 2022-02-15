package org.jetbrains.jewel.theme.toolbox.styles

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.ShapeStroke
import org.jetbrains.jewel.styles.ControlStyle
import org.jetbrains.jewel.styles.Styles
import org.jetbrains.jewel.styles.localNotProvided
import org.jetbrains.jewel.theme.toolbox.Palette
import org.jetbrains.jewel.theme.toolbox.ToolboxMetrics
import org.jetbrains.jewel.theme.toolbox.components.SwitchState

typealias SwitchStyle = ControlStyle<SwitchAppearance, SwitchState>

@Immutable
data class SwitchAppearance(
    val width: Dp = 22.dp,
    val height: Dp = 14.dp,

    val backgroundColor: Color = Color.Blue,
    val shapeStroke: ShapeStroke? = null,
    val shape: Shape = RoundedCornerShape(8.dp),

    val thumbSize: Dp = 10.dp,
    val thumbPadding: Dp = 2.dp,
    val thumbBackgroundColor: Color = Color.White,
    val thumbBorderStroke: ShapeStroke? = null,
    val thumbShape: Shape = CircleShape,
)

val LocalSwitchStyle = compositionLocalOf<SwitchStyle> { localNotProvided() }
val Styles.switch: SwitchStyle
    @Composable
    @ReadOnlyComposable
    get() = LocalSwitchStyle.current

fun SwitchStyle(palette: Palette, metrics: ToolboxMetrics) = SwitchStyle {
    default {
        state(
            SwitchState.On, SwitchAppearance(
            thumbBackgroundColor = palette.controlContent,
            backgroundColor = palette.controlBackground,
            width = metrics.base * 4 - metrics.adornmentsThickness,
            height = metrics.base * 2 + metrics.adornmentsThickness,
            shape = RoundedCornerShape(metrics.cornerSize),
            thumbSize = metrics.base * 2 - metrics.adornmentsThickness,
            thumbPadding = metrics.adornmentsThickness
        )
        )
        state(
            SwitchState.Off, SwitchAppearance(
            thumbBackgroundColor = palette.controlContent,
            backgroundColor = palette.controlBackgroundOff,
            width = metrics.base * 4 - metrics.adornmentsThickness,
            height = metrics.base * 2 + metrics.adornmentsThickness,
            shape = RoundedCornerShape(metrics.cornerSize),
            thumbSize = metrics.base * 2 - metrics.adornmentsThickness,
            thumbPadding = metrics.adornmentsThickness
        )
        )
    }
}
