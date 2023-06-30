package org.jetbrains.jewel

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import org.jetbrains.jewel.foundation.Stroke
import org.jetbrains.jewel.foundation.border

@Composable
fun BaseChip(
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    enabled: Boolean = true,
    defaults: ChipDefaults = IntelliJTheme.chipDefaults,
    colors: ChipColors = defaults.chipColors(),
    onChipClick: () -> Unit = {},
    content: @Composable () -> Unit
) {
    var chipState by remember(interactionSource) {
        mutableStateOf(ChipState.of(enabled = enabled))
    }
    remember(enabled) {
        chipState = chipState.copy(enabled = enabled)
    }

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> chipState = chipState.copy(pressed = true)
                is PressInteraction.Cancel, is PressInteraction.Release -> chipState = chipState.copy(pressed = false)
                is HoverInteraction.Enter -> chipState = chipState.copy(hovered = true)
                is HoverInteraction.Exit -> chipState = chipState.copy(hovered = false)
                is FocusInteraction.Focus -> chipState = chipState.copy(focused = true)
                is FocusInteraction.Unfocus -> chipState = chipState.copy(focused = false)
            }
        }
    }
    val shape = defaults.shape()
    Row(
        modifier = modifier
            .clickable(
                onClick = onChipClick,
                enabled = enabled,
                role = Role.Button,
                interactionSource = interactionSource,
                indication = null
            )
            .padding(defaults.contentPadding())
            .defaultMinSize(defaults.minWidth(), defaults.minHeight())
            .clip(shape)
            .background(colors.backgroundBrush(chipState).value, shape)
            .border(colors.borderStroke(chipState).value, shape)
            .border(colors.haloStroke(chipState).value, shape),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        content()
    }
}

@Stable
interface ChipDefaults {

    @Composable
    fun shape(): Shape

    @Composable
    fun contentPadding(): PaddingValues

    @Composable
    fun minWidth(): Dp

    @Composable
    fun minHeight(): Dp

    @Composable
    fun chipColors(): ChipColors
}

@Stable
interface ChipColors {
    @Composable
    fun backgroundBrush(state: ChipState): State<Brush>

    @Composable
    fun contentColor(state: ChipState): State<Color>

    @Composable
    fun borderStroke(state: ChipState): State<Stroke>

    @Composable
    fun haloStroke(state: ChipState): State<Stroke>
}

@Immutable
@JvmInline
value class ChipState(val state: ULong) {

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

    fun copy(enabled: Boolean = isEnabled, focused: Boolean = isFocused, hovered: Boolean = isHovered, pressed: Boolean = isPressed): ChipState =
        of(enabled, focused, hovered, pressed)

    override fun toString(): String = "ChipState(enabled=$isEnabled, focused=$isFocused, Hovered=$isHovered, pressed=$isPressed)"

    companion object {

        private val Enabled = 1UL shl 0
        private val Focused = 1UL shl 1
        private val Hovered = 1UL shl 2
        private val Pressed = 1UL shl 3

        fun of(enabled: Boolean = true, focused: Boolean = false, hovered: Boolean = false, pressed: Boolean = false): ChipState {
            return ChipState(
                state = (if (enabled) Enabled else 0UL) or
                    (if (focused) Focused else 0UL) or
                    (if (hovered) Hovered else 0UL) or
                    if (pressed) Pressed else 0UL
            )
        }
    }
}

fun chipColors(
    backgroundBrush: Brush,
    contentColor: Color,
    borderStroke: Stroke,
    focusedBackground: Brush,
    focusedContentColor: Color,
    focusedBorderStroke: Stroke,
    focusedHaloStroke: Stroke,
    hoveredBackground: Brush,
    hoveredContentColor: Color,
    hoveredBorderStroke: Stroke,
    pressedBackground: Brush,
    pressedContentColor: Color,
    pressedBorderStroke: Stroke,
    disabledBackground: Brush,
    disabledContentColor: Color,
    disabledBorderStroke: Stroke
): ChipColors = DefaultChipColors(
    backgroundBrush = backgroundBrush,
    contentColor = contentColor,
    borderStroke = borderStroke,
    focusedBackground = focusedBackground,
    focusedContentColor = focusedContentColor,
    focusedBorderStroke = focusedBorderStroke,
    focusedHaloStroke = focusedHaloStroke,
    hoveredBackground = hoveredBackground,
    hoveredContentColor = hoveredContentColor,
    hoveredBorderStroke = hoveredBorderStroke,
    pressedBackground = pressedBackground,
    pressedContentColor = pressedContentColor,
    pressedBorderStroke = pressedBorderStroke,
    disabledBackground = disabledBackground,
    disabledContentColor = disabledContentColor,
    disabledBorderStroke = disabledBorderStroke
)

@Immutable
private data class DefaultChipColors(
    // default
    private val backgroundBrush: Brush,
    private val contentColor: Color,
    private val borderStroke: Stroke,
    // focussed
    private val focusedBackground: Brush,
    private val focusedContentColor: Color,
    private val focusedBorderStroke: Stroke,
    private val focusedHaloStroke: Stroke,
    // hovered
    private val hoveredBackground: Brush,
    private val hoveredContentColor: Color,
    private val hoveredBorderStroke: Stroke,
    // pressed
    private val pressedBackground: Brush,
    private val pressedContentColor: Color,
    private val pressedBorderStroke: Stroke,
    // disabled
    private val disabledBackground: Brush,
    private val disabledContentColor: Color,
    private val disabledBorderStroke: Stroke
) : ChipColors {

    @Composable
    override fun backgroundBrush(state: ChipState): State<Brush> {
        return rememberUpdatedState(
            when {
                !state.isEnabled -> disabledBackground
                state.isFocused -> focusedBackground
                state.isPressed -> pressedBackground
                state.isHovered -> hoveredBackground
                else -> backgroundBrush
            }
        )
    }

    @Composable
    override fun contentColor(state: ChipState): State<Color> {
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
    override fun borderStroke(state: ChipState): State<Stroke> {
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
    override fun haloStroke(state: ChipState): State<Stroke> {
        return rememberUpdatedState(
            when {
                !state.isEnabled -> Stroke.None
                state.isFocused -> focusedHaloStroke
                else -> Stroke.None
            }
        )
    }
}

internal val LocalChipDefaults = staticCompositionLocalOf<ChipDefaults> {
    error("No ChipDefaults provided")
}
