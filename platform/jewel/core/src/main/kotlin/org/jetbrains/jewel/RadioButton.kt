package org.jetbrains.jewel

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import org.jetbrains.jewel.foundation.Stroke
import org.jetbrains.jewel.foundation.border

@Composable
fun RadioButton(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isError: Boolean = false,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    defaults: RadioButtonDefaults = IntelliJTheme.radioButtonDefaults,
    colors: RadioButtonColors = defaults.colors(),
    shape: Shape = defaults.shape()
) = RadioButtonImpl(
    selected = selected,
    onClick = onClick,
    modifier = modifier,
    enabled = enabled,
    isError = isError,
    interactionSource = interactionSource,
    defaults = defaults,
    colors = colors,
    shape = shape
)

@Composable
fun RadioButtonRow(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isError: Boolean = false,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    defaults: RadioButtonDefaults = IntelliJTheme.radioButtonDefaults,
    colors: RadioButtonColors = defaults.colors(),
    shape: Shape = defaults.shape()
) = RadioButtonImpl(
    selected = selected,
    onClick = onClick,
    modifier = modifier,
    enabled = enabled,
    isError = isError,
    interactionSource = interactionSource,
    defaults = defaults,
    colors = colors,
    shape = shape
) {
    Text(text)
}

@Composable
fun RadioButtonRow(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isError: Boolean = false,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    defaults: RadioButtonDefaults = IntelliJTheme.radioButtonDefaults,
    colors: RadioButtonColors = defaults.colors(),
    shape: Shape = defaults.shape(),
    content: @Composable RowScope.() -> Unit
) = RadioButtonImpl(
    selected = selected,
    onClick = onClick,
    modifier = modifier,
    enabled = enabled,
    isError = isError,
    interactionSource = interactionSource,
    defaults = defaults,
    colors = colors,
    shape = shape,
    content = content
)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun RadioButtonImpl(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isError: Boolean = false,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    defaults: RadioButtonDefaults = IntelliJTheme.radioButtonDefaults,
    colors: RadioButtonColors = defaults.colors(),
    shape: Shape = defaults.shape(),
    content: (@Composable RowScope.() -> Unit)? = null
) {
    var radioButtonState by remember(interactionSource) {
        mutableStateOf(RadioButtonState.of(selected = selected, enabled = enabled))
    }
    remember(selected, isError, enabled) {
        radioButtonState = radioButtonState.copy(selected = selected, error = isError, enabled = enabled)
    }
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> radioButtonState = radioButtonState.copy(pressed = true)
                is PressInteraction.Cancel, is PressInteraction.Release -> radioButtonState = radioButtonState.copy(pressed = false)
                is HoverInteraction.Enter -> radioButtonState = radioButtonState.copy(hovered = true)
                is HoverInteraction.Exit -> radioButtonState = radioButtonState.copy(hovered = false)
                is FocusInteraction.Focus -> radioButtonState = radioButtonState.copy(focused = true)
                is FocusInteraction.Unfocus -> radioButtonState = radioButtonState.copy(focused = false)
            }
        }
    }

    val wrapperModifier = modifier.selectable(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        role = Role.RadioButton,
        interactionSource = interactionSource,
        indication = null
    )

    val radioButtonModifier = Modifier.size(defaults.width(), defaults.height())
        .background(colors.backgroundColor(radioButtonState).value, shape)
        .border(colors.borderStroke(radioButtonState).value, shape)
        .border(colors.haloStroke(radioButtonState).value, shape)
        .composed {
            defaults.checkmark(radioButtonState).value?.let {
                val colorFilter = ColorFilter.tint(colors.checkmarkColor(radioButtonState).value)
                paint(it, colorFilter = colorFilter)
            } ?: this
        }

    if (content == null) {
        Box(wrapperModifier.then(radioButtonModifier))
    } else {
        Row(
            wrapperModifier,
            horizontalArrangement = Arrangement.spacedBy(defaults.contentSpacing()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(radioButtonModifier)
            CompositionLocalProvider(
                LocalTextStyle provides defaults.textStyle(),
                LocalTextColor provides colors.contentColor(radioButtonState).value
            ) {
                content()
            }
        }
    }
}

@Immutable
@JvmInline
value class RadioButtonState(val state: ULong) {

    @Stable
    val isSelected: Boolean
        get() = state and Selected != 0UL

    @Stable
    val isEnabled: Boolean
        get() = state and Enabled != 0UL

    @Stable
    val isFocused: Boolean
        get() = state and Focused != 0UL

    @Stable
    val isError: Boolean
        get() = state and Error != 0UL

    @Stable
    val isHovered: Boolean
        get() = state and Hovered != 0UL

    @Stable
    val isPressed: Boolean
        get() = state and Pressed != 0UL

    fun copy(
        selected: Boolean = this.isSelected,
        enabled: Boolean = this.isEnabled,
        focused: Boolean = this.isFocused,
        error: Boolean = this.isError,
        hovered: Boolean = this.isHovered,
        pressed: Boolean = this.isPressed
    ) = of(
        selected = selected,
        enabled = enabled,
        focused = focused,
        error = error,
        hovered = hovered,
        pressed = pressed
    )

    override fun toString(): String =
        "RadioButtonState(selected=$isSelected, enabled=$isEnabled, focused=$isFocused, error=$isError, hovered=$isHovered, pressed=$isPressed)"

    companion object {

        private val Enabled = 1UL shl 0
        private val Focused = 1UL shl 1
        private val Error = 1UL shl 2
        private val Hovered = 1UL shl 3
        private val Pressed = 1UL shl 4
        private val Selected = 1UL shl 5

        fun of(
            selected: Boolean,
            enabled: Boolean = true,
            focused: Boolean = false,
            error: Boolean = false,
            pressed: Boolean = false,
            hovered: Boolean = false
        ): RadioButtonState {
            var state = 0UL
            if (selected) state = state or Selected
            if (enabled) state = state or Enabled
            if (focused) state = state or Focused
            if (error) state = state or Error
            if (pressed) state = state or Pressed
            if (hovered) state = state or Hovered
            return RadioButtonState(state)
        }
    }
}

@Stable
interface RadioButtonColors {

    @Composable
    fun checkmarkColor(state: RadioButtonState): State<Color>

    @Composable
    fun backgroundColor(state: RadioButtonState): State<Color>

    @Composable
    fun contentColor(state: RadioButtonState): State<Color>

    @Composable
    fun borderStroke(state: RadioButtonState): State<Stroke>

    @Composable
    fun haloStroke(state: RadioButtonState): State<Stroke>
}

@Stable
interface RadioButtonDefaults {

    @Composable
    fun colors(): RadioButtonColors

    @Composable
    fun shape(): Shape

    @Composable
    fun width(): Dp

    @Composable
    fun height(): Dp

    @Composable
    fun contentSpacing(): Dp

    @Composable
    fun textStyle(): TextStyle

    @Composable
    fun checkmark(state: RadioButtonState): State<Painter?>
}

fun radioButtonColors(
    checkmarkColor: Color,
    textColor: Color,
    unselectedBackground: Color,
    unselectedStroke: Stroke,
    unselectedFocusedStroke: Stroke,
    unselectedFocusHoloStroke: Stroke,
    unselectedErrorHoloStroke: Stroke,
    unselectedHoveredBackground: Color,
    unselectedHoveredStroke: Stroke,
    unselectedDisabledBackground: Color,
    unselectedDisabledStroke: Stroke,
    selectedBackground: Color,
    selectedStroke: Stroke,
    selectedFocusedStroke: Stroke,
    selectedFocusHoloStroke: Stroke,
    selectedErrorHoloStroke: Stroke,
    selectedHoveredBackground: Color,
    selectedHoveredStroke: Stroke,
    selectedDisabledBackground: Color,
    selectedDisabledStroke: Stroke,
    disabledCheckmarkColor: Color,
    disabledTextColor: Color
): RadioButtonColors = DefaultRadioButtonColors(
    checkmarkColor = checkmarkColor,
    textColor = textColor,
    unselectedBackground = unselectedBackground,
    unselectedStroke = unselectedStroke,
    unselectedFocusedStroke = unselectedFocusedStroke,
    unselectedFocusHoloStroke = unselectedFocusHoloStroke,
    unselectedErrorHoloStroke = unselectedErrorHoloStroke,
    unselectedHoveredBackground = unselectedHoveredBackground,
    unselectedHoveredStroke = unselectedHoveredStroke,
    unselectedDisabledBackground = unselectedDisabledBackground,
    unselectedDisabledStroke = unselectedDisabledStroke,
    selectedBackground = selectedBackground,
    selectedStroke = selectedStroke,
    selectedFocusedStroke = selectedFocusedStroke,
    selectedFocusHoloStroke = selectedFocusHoloStroke,
    selectedErrorHoloStroke = selectedErrorHoloStroke,
    selectedHoveredBackground = selectedHoveredBackground,
    selectedHoveredStroke = selectedHoveredStroke,
    selectedDisabledBackground = selectedDisabledBackground,
    selectedDisabledStroke = selectedDisabledStroke,
    disabledCheckmarkColor = disabledCheckmarkColor,
    disabledTextColor = disabledTextColor
)

@Immutable
private data class DefaultRadioButtonColors(
    private val checkmarkColor: Color,
    private val textColor: Color,

    private val unselectedBackground: Color,
    private val unselectedStroke: Stroke,
    private val unselectedFocusedStroke: Stroke,
    private val unselectedFocusHoloStroke: Stroke,
    private val unselectedErrorHoloStroke: Stroke,
    private val unselectedHoveredBackground: Color,
    private val unselectedHoveredStroke: Stroke,
    private val unselectedDisabledBackground: Color,
    private val unselectedDisabledStroke: Stroke,

    private val selectedBackground: Color,
    private val selectedStroke: Stroke,
    private val selectedFocusedStroke: Stroke,
    private val selectedFocusHoloStroke: Stroke,
    private val selectedErrorHoloStroke: Stroke,
    private val selectedHoveredBackground: Color,
    private val selectedHoveredStroke: Stroke,
    private val selectedDisabledBackground: Color,
    private val selectedDisabledStroke: Stroke,

    private val disabledCheckmarkColor: Color,
    private val disabledTextColor: Color
) : RadioButtonColors {

    @Composable
    override fun checkmarkColor(state: RadioButtonState): State<Color> {
        return rememberUpdatedState(
            when {
                !state.isEnabled -> disabledCheckmarkColor
                else -> checkmarkColor
            }
        )
    }

    @Composable
    override fun borderStroke(state: RadioButtonState): State<Stroke> {
        return rememberUpdatedState(
            when {
                !state.isEnabled -> if (state.isSelected) {
                    selectedDisabledStroke
                } else {
                    unselectedDisabledStroke
                }

                state.isFocused || state.isError -> if (state.isSelected) {
                    selectedFocusedStroke
                } else {
                    unselectedFocusedStroke
                }

                state.isHovered -> if (state.isSelected) {
                    selectedHoveredStroke
                } else {
                    unselectedHoveredStroke
                }

                !state.isSelected -> unselectedStroke
                else -> selectedStroke
            }
        )
    }

    @Composable
    override fun haloStroke(state: RadioButtonState): State<Stroke> {
        return rememberUpdatedState(
            when {
                !state.isEnabled -> Stroke.None
                state.isError -> if (state.isSelected) {
                    selectedErrorHoloStroke
                } else {
                    unselectedErrorHoloStroke
                }

                state.isFocused -> if (state.isSelected) {
                    selectedFocusHoloStroke
                } else {
                    unselectedFocusHoloStroke
                }

                else -> Stroke.None
            }
        )
    }

    @Composable
    override fun backgroundColor(state: RadioButtonState): State<Color> {
        return rememberUpdatedState(
            when {
                !state.isEnabled -> if (state.isSelected) {
                    selectedDisabledBackground
                } else {
                    unselectedDisabledBackground
                }

                state.isFocused -> if (state.isSelected) {
                    selectedBackground
                } else {
                    unselectedBackground
                }

                state.isHovered || state.isPressed -> if (state.isSelected) {
                    selectedHoveredBackground
                } else {
                    unselectedHoveredBackground
                }

                !state.isSelected -> unselectedBackground
                else -> selectedBackground
            }
        )
    }

    @Composable
    override fun contentColor(state: RadioButtonState): State<Color> {
        return rememberUpdatedState(
            when {
                !state.isEnabled -> disabledTextColor
                else -> textColor
            }
        )
    }
}

internal val LocalRadioButtonDefaults = staticCompositionLocalOf<RadioButtonDefaults> {
    error("No RadioButtonDefaults provided")
}
