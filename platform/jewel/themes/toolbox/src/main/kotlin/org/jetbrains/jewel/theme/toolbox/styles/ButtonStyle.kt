package org.jetbrains.jewel.theme.toolbox.styles

import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import org.jetbrains.jewel.ShapeStroke
import org.jetbrains.jewel.animateShapeStroke
import org.jetbrains.jewel.components.state.AppearanceTransitionState
import org.jetbrains.jewel.components.state.ButtonMouseState
import org.jetbrains.jewel.components.state.ButtonState
import org.jetbrains.jewel.styles.ControlStyle
import org.jetbrains.jewel.styles.Styles
import org.jetbrains.jewel.styles.localNotProvided
import org.jetbrains.jewel.theme.toolbox.Palette
import org.jetbrains.jewel.theme.toolbox.ToolboxMetrics
import org.jetbrains.jewel.theme.toolbox.ToolboxTypography
import org.jetbrains.jewel.toBrush

@Immutable
data class ButtonAppearance(
    val textStyle: TextStyle = TextStyle.Default,
    val background: Brush? = null,
    val shapeStroke: ShapeStroke? = null,
    val shape: Shape,

    val contentPadding: PaddingValues,
    val minWidth: Dp,
    val minHeight: Dp,

    val haloStroke: ShapeStroke? = null,
    val haloShape: Shape = shape,

    val shadowColor: Color? = null,
    val shadowElevation: Dp? = null
)

@Composable
fun updateButtonAppearanceTransition(appearance: ButtonAppearance): AppearanceTransitionState {
    val transition = updateTransition(appearance)
    val background = mutableStateOf(appearance.background)
    val shapeStroke = transition.animateShapeStroke(label = "AnimateShapeStroke") { it.shapeStroke }
    val haloStroke = transition.animateShapeStroke(label = "AnimateHaloStroke") { it.haloStroke }
    return AppearanceTransitionState(background, shapeStroke, haloStroke)
}

typealias ButtonStyle = ControlStyle<ButtonAppearance, ButtonState>

val LocalButtonStyle = compositionLocalOf<ButtonStyle> { localNotProvided() }
val Styles.button: ButtonStyle
    @Composable
    @ReadOnlyComposable
    get() = LocalButtonStyle.current

val LocalIconButtonStyle = compositionLocalOf<ButtonStyle> { localNotProvided() }
val Styles.iconButton: ButtonStyle
    @Composable
    @ReadOnlyComposable
    get() = LocalIconButtonStyle.current

fun ButtonStyle(palette: Palette, metrics: ToolboxMetrics, typography: ToolboxTypography) = ButtonStyle {
    default {
        for (focused in listOf(false, true)) {
            val haloStroke = if (focused)
                ShapeStroke(metrics.adornmentsThickness, palette.controlAdornmentsHover.toBrush())
            else
                null
            val appearance = ButtonAppearance(
                textStyle = typography.control.copy(palette.controlContent),
                background = palette.controlBackground.toBrush(),
                shape = RoundedCornerShape(metrics.cornerSize),
                contentPadding = PaddingValues(metrics.largePadding, metrics.smallPadding),
                minWidth = metrics.base * 4,
                minHeight = metrics.base * 2,
                haloStroke = haloStroke
            )
            state(ButtonState(focused = focused), appearance)
            state(
                ButtonState(ButtonMouseState.Pressed, focused = focused),
                appearance.copy(
                    textStyle = typography.control.copy(palette.controlContentActive),
                    background = palette.controlBackgroundActive.toBrush(),
                )
            )
            state(
                ButtonState(ButtonMouseState.Hovered, focused = focused),
                appearance.copy(
                    textStyle = typography.control.copy(palette.controlContent),
                    background = palette.controlBackgroundHover.toBrush(),
                )
            )
            state(
                ButtonState(enabled = false, focused = focused),
                appearance.copy(
                    textStyle = typography.control.copy(palette.controlContentDisabled),
                    background = palette.controlBackgroundDisabled.toBrush(),
                )
            )
        }
    }
}
