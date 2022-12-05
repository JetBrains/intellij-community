package org.jetbrains.jewel.styles

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
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.Insets
import org.jetbrains.jewel.IntelliJMetrics
import org.jetbrains.jewel.IntelliJPalette
import org.jetbrains.jewel.ShapeStroke
import org.jetbrains.jewel.animateShapeStroke
import org.jetbrains.jewel.components.state.ButtonAppearanceTransitionState
import org.jetbrains.jewel.components.state.ButtonMouseState
import org.jetbrains.jewel.components.state.ButtonState
import org.jetbrains.jewel.toBrush

@Immutable
data class ButtonAppearance(
    val textStyle: TextStyle = TextStyle.Default,
    val background: Brush? = null,
    val shapeStroke: ShapeStroke<*>? = null,
    val shape: Shape,

    val contentPadding: PaddingValues,
    val minWidth: Dp,
    val minHeight: Dp,

    val haloStroke: ShapeStroke<*>? = null,
    val haloShape: Shape = shape,

    val shadowColor: Color? = null,
    val shadowElevation: Dp? = null
)

typealias ButtonStyle = ControlStyle<ButtonAppearance, ButtonState>

val LocalButtonStyle = compositionLocalOf<ButtonStyle> { localNotProvided() }
val Styles.button: ButtonStyle
    @Composable @ReadOnlyComposable get() = LocalButtonStyle.current

val LocalIconButtonStyle = compositionLocalOf<ButtonStyle> { localNotProvided() }

val Styles.iconButton: ButtonStyle
    @Composable @ReadOnlyComposable get() = LocalIconButtonStyle.current

@Composable
fun updateButtonAppearanceTransition(appearance: ButtonAppearance): ButtonAppearanceTransitionState {
    val transition = updateTransition(appearance)
    val background = mutableStateOf(appearance.background)
    val shapeStroke = transition.animateShapeStroke(label = "AnimateShapeStroke") { it.shapeStroke }
    val haloStroke = transition.animateShapeStroke(label = "AnimateHaloStroke") { it.haloStroke }
    return ButtonAppearanceTransitionState(background, shapeStroke, haloStroke)
}

enum class IntelliJButtonStyleVariations {
    DefaultButton
}

fun ButtonStyle(
    palette: IntelliJPalette,
    metrics: IntelliJMetrics,
    controlTextStyle: TextStyle,
    contentPadding: PaddingValues = metrics.button.padding,
    shape: Shape = RoundedCornerShape(metrics.button.arc),
    minSize: DpSize = DpSize(72.dp, 16.dp),
) = ButtonStyle {
    val focusHaloStroke = ShapeStroke.SolidColor(metrics.controlFocusHaloWidth, palette.controlFocusHalo)
    val defaultAppearance = ButtonAppearance(
        textStyle = controlTextStyle.copy(palette.button.foreground),
        background = palette.button.background,
        shape = shape,
        contentPadding = contentPadding,
        minWidth = minSize.width,
        minHeight = minSize.height,
        shapeStroke = ShapeStroke.Brush(metrics.button.strokeWidth, palette.button.stroke, Insets(metrics.button.strokeWidth)),
        haloStroke = null
    )

    default {
        for (focused in listOf(false, true)) {
            val appearance = defaultAppearance.copy(haloStroke = if (focused) focusHaloStroke else null)

            populateStates(appearance, focused, focusHaloStroke, controlTextStyle, palette, metrics)
        }
    }

    variation(IntelliJButtonStyleVariations.DefaultButton) {
        for (focused in listOf(false, true)) {
            val strokeBrush = if (focused) palette.button.defaultStrokeFocused.toBrush() else palette.button.defaultStroke
            val appearance = defaultAppearance.copy(
                background = palette.button.defaultBackground,
                textStyle = controlTextStyle.copy(color = palette.button.defaultForeground),
                shapeStroke = ShapeStroke.Brush(metrics.button.strokeWidth, strokeBrush, Insets(metrics.button.strokeWidth)),
                haloStroke = if (focused) focusHaloStroke else null,
            )

            populateStates(appearance, focused, focusHaloStroke, controlTextStyle, palette, metrics)
        }
    }
}

private fun ControlStyle.ControlVariationBuilder<ButtonAppearance, ButtonState>.populateStates(
    appearance: ButtonAppearance,
    focused: Boolean,
    focusHaloStroke: ShapeStroke<*>,
    controlTextStyle: TextStyle,
    palette: IntelliJPalette,
    metrics: IntelliJMetrics
) {
    state(ButtonState(focused = focused), appearance)
    state(ButtonState(ButtonMouseState.Pressed, focused = focused), appearance.copy(haloStroke = focusHaloStroke))
    state(ButtonState(ButtonMouseState.Hovered, focused = focused), appearance)
    state(
        ButtonState(enabled = false, focused = focused),
        appearance.copy(
            textStyle = controlTextStyle.copy(palette.button.foregroundDisabled),
            background = Color.Transparent.toBrush(),
            shapeStroke = ShapeStroke.SolidColor(metrics.button.strokeWidth, palette.controlStrokeDisabled, Insets(metrics.button.strokeWidth))
        )
    )
}
