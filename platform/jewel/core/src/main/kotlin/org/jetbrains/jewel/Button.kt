package org.jetbrains.jewel

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import org.jetbrains.jewel.foundation.Stroke
import org.jetbrains.jewel.foundation.border

@Composable
fun DefaultButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    defaults: ButtonDefaults = IntelliJTheme.buttonDefaults,
    colors: ButtonColors = defaults.primaryButtonColors(),
    shape: Shape = defaults.shape(),
    content: @Composable RowScope.() -> Unit
) {
    ButtonImpl(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        interactionSource = interactionSource,
        defaults = defaults,
        colors = colors,
        shape = shape,
        content = content
    )
}

@Composable
fun OutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    defaults: ButtonDefaults = IntelliJTheme.buttonDefaults,
    colors: ButtonColors = defaults.outlinedButtonColors(),
    shape: Shape = defaults.shape(),
    content: @Composable RowScope.() -> Unit
) {
    ButtonImpl(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        interactionSource = interactionSource,
        defaults = defaults,
        colors = colors,
        shape = shape,
        content = content
    )
}

@Composable
private fun ButtonImpl(
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    interactionSource: MutableInteractionSource,
    defaults: ButtonDefaults,
    colors: ButtonColors,
    shape: Shape,
    content: @Composable RowScope.() -> Unit
) {
    var buttonState by remember(interactionSource) {
        mutableStateOf(ButtonState.of(enabled = enabled))
    }

    remember(enabled) {
        buttonState = buttonState.copy(enabled = enabled)
    }

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> buttonState = buttonState.copy(pressed = true)
                is PressInteraction.Cancel, is PressInteraction.Release -> buttonState = buttonState.copy(pressed = false)
                is HoverInteraction.Enter -> buttonState = buttonState.copy(hovered = true)
                is HoverInteraction.Exit -> buttonState = buttonState.copy(hovered = false)
                is FocusInteraction.Focus -> buttonState = buttonState.copy(focused = true)
                is FocusInteraction.Unfocus -> buttonState = buttonState.copy(focused = false)
            }
        }
    }

    Box(
        modifier.clickable(
            onClick = onClick,
            enabled = enabled,
            role = Role.Button,
            interactionSource = interactionSource,
            indication = null
        ).background(colors.backgroundBrush(buttonState).value, shape)
            .border(colors.borderStroke(buttonState).value, shape)
            .border(colors.haloStroke(buttonState).value, shape),
        propagateMinConstraints = true
    ) {
        CompositionLocalProvider(
            LocalTextColor provides colors.contentColor(buttonState).value
        ) {
            Row(
                Modifier
                    .defaultMinSize(defaults.minWidth(), defaults.minHeight())
                    .padding(defaults.contentPadding()),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                content = content
            )
        }
    }
}

@Stable
interface ButtonColors {

    @Composable
    fun backgroundBrush(state: ButtonState): State<Brush>

    @Composable
    fun contentColor(state: ButtonState): State<Color>

    @Composable
    fun borderStroke(state: ButtonState): State<Stroke>

    @Composable
    fun haloStroke(state: ButtonState): State<Stroke>
}

@Stable
interface ButtonDefaults {

    @Composable
    fun shape(): Shape

    @Composable
    fun contentPadding(): PaddingValues

    @Composable
    fun minWidth(): Dp

    @Composable
    fun minHeight(): Dp

    @Composable
    fun primaryButtonColors(): ButtonColors

    @Composable
    fun outlinedButtonColors(): ButtonColors
}

@Immutable
@JvmInline
value class ButtonState(val state: ULong) {

    @Stable
    val isEnabled: Boolean
        get() = state and Enabled != 0UL

    @Stable
    val isFocused: Boolean
        get() = state and Focused != 0UL

    @Stable
    val isHovered: Boolean
        get() = state and Hovered != 0UL

    @Stable
    val isPressed: Boolean
        get() = state and Pressed != 0UL

    fun copy(enabled: Boolean = isEnabled, focused: Boolean = isFocused, pressed: Boolean = isPressed, hovered: Boolean = isHovered) =
        of(enabled, focused, pressed, hovered)

    override fun toString(): String = "ButtonState(enabled=$isEnabled, focused=$isFocused, pressed=$isPressed, hovered=$isHovered)"

    companion object {

        private val Enabled = 1UL shl 0
        private val Focused = 1UL shl 1
        private val Hovered = 1UL shl 2
        private val Pressed = 1UL shl 3

        fun of(enabled: Boolean = true, focused: Boolean = false, pressed: Boolean = false, hovered: Boolean = false): ButtonState {
            var state = 0UL
            if (enabled) state = state or Enabled
            if (focused) state = state or Focused
            if (pressed) state = state or Pressed
            if (hovered) state = state or Hovered
            return ButtonState(state)
        }
    }
}

fun buttonColors(
    backgroundBrush: Brush,
    contentColor: Color,
    borderStroke: Stroke,
    disabledBackgroundBrush: Brush,
    disabledContentColor: Color,
    disabledBorderStroke: Stroke,
    hoveredBackgroundBrush: Brush,
    hoveredContentColor: Color,
    hoveredBorderStroke: Stroke,
    pressedBackgroundBrush: Brush,
    pressedContentColor: Color,
    pressedBorderStroke: Stroke,
    focusedBackgroundBrush: Brush,
    focusedContentColor: Color,
    focusedBorderStroke: Stroke,
    focusHaloStroke: Stroke
): ButtonColors = DefaultButtonColors(
    backgroundBrush = backgroundBrush,
    contentColor = contentColor,
    borderStroke = borderStroke,
    disabledBackgroundBrush = disabledBackgroundBrush,
    disabledContentColor = disabledContentColor,
    disabledBorderStroke = disabledBorderStroke,
    hoveredBackgroundBrush = hoveredBackgroundBrush,
    hoveredContentColor = hoveredContentColor,
    hoveredBorderStroke = hoveredBorderStroke,
    pressedBackgroundBrush = pressedBackgroundBrush,
    pressedContentColor = pressedContentColor,
    pressedBorderStroke = pressedBorderStroke,
    focusedBackgroundBrush = focusedBackgroundBrush,
    focusedContentColor = focusedContentColor,
    focusedBorderStroke = focusedBorderStroke,
    focusHaloStroke = focusHaloStroke
)

@Immutable
private data class DefaultButtonColors(
    private val backgroundBrush: Brush,
    private val contentColor: Color,
    private val borderStroke: Stroke,
    private val disabledBackgroundBrush: Brush,
    private val disabledContentColor: Color,
    private val disabledBorderStroke: Stroke,
    private val hoveredBackgroundBrush: Brush,
    private val hoveredContentColor: Color,
    private val hoveredBorderStroke: Stroke,
    private val pressedBackgroundBrush: Brush,
    private val pressedContentColor: Color,
    private val pressedBorderStroke: Stroke,
    private val focusedBackgroundBrush: Brush,
    private val focusedContentColor: Color,
    private val focusedBorderStroke: Stroke,
    private val focusHaloStroke: Stroke
) : ButtonColors {

    @Composable
    override fun backgroundBrush(state: ButtonState): State<Brush> {
        return rememberUpdatedState(
            when {
                !state.isEnabled -> disabledBackgroundBrush
                state.isFocused -> focusedBackgroundBrush
                state.isPressed -> pressedBackgroundBrush
                state.isHovered -> hoveredBackgroundBrush
                else -> backgroundBrush
            }
        )
    }

    @Composable
    override fun contentColor(state: ButtonState): State<Color> {
        return rememberUpdatedState(
            when {
                !state.isEnabled -> disabledContentColor
                state.isFocused -> focusedContentColor
                state.isPressed -> pressedContentColor
                state.isHovered -> hoveredContentColor
                else -> contentColor
            }
        )
    }

    @Composable
    override fun borderStroke(state: ButtonState): State<Stroke> {
        return rememberUpdatedState(
            when {
                !state.isEnabled -> disabledBorderStroke
                state.isFocused -> focusedBorderStroke
                state.isPressed -> pressedBorderStroke
                state.isHovered -> hoveredBorderStroke
                else -> borderStroke
            }
        )
    }

    @Composable
    override fun haloStroke(state: ButtonState): State<Stroke> {
        return rememberUpdatedState(
            when {
                !state.isEnabled -> Stroke.None
                state.isFocused -> focusHaloStroke
                else -> Stroke.None
            }
        )
    }
}

internal val LocalButtonDefaults = staticCompositionLocalOf<ButtonDefaults> {
    error("No ButtonDefaults provided")
}
