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
import androidx.compose.foundation.selection.triStateToggleable
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
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import org.jetbrains.jewel.foundation.Stroke
import org.jetbrains.jewel.foundation.border

@Composable
fun Checkbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isError: Boolean = false,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    defaults: CheckboxDefaults = IntelliJTheme.checkboxDefaults,
    colors: CheckboxColors = defaults.colors(),
    shape: Shape = defaults.shape()
) = CheckboxImpl(
    state = ToggleableState(checked),
    onClick = {
        onCheckedChange.invoke(!checked)
    },
    modifier = modifier,
    enabled = enabled,
    isError = isError,
    interactionSource = interactionSource,
    defaults = defaults,
    colors = colors,
    shape = shape
)

@Composable
fun TriStateCheckbox(
    state: ToggleableState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isError: Boolean = false,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    defaults: CheckboxDefaults = IntelliJTheme.checkboxDefaults,
    colors: CheckboxColors = defaults.colors(),
    shape: Shape = defaults.shape()
) = CheckboxImpl(
    state = state,
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
fun TriStateCheckboxRow(
    text: String,
    state: ToggleableState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isError: Boolean = false,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    defaults: CheckboxDefaults = IntelliJTheme.checkboxDefaults,
    colors: CheckboxColors = defaults.colors(),
    shape: Shape = defaults.shape()
) = CheckboxImpl(
    state = state,
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
fun CheckboxRow(
    text: String,
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isError: Boolean = false,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    defaults: CheckboxDefaults = IntelliJTheme.checkboxDefaults,
    colors: CheckboxColors = defaults.colors(),
    shape: Shape = defaults.shape()
) = CheckboxImpl(
    state = ToggleableState(checked),
    onClick = {
        onCheckedChange?.invoke(!checked)
    },
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
fun CheckboxRow(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isError: Boolean = false,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    defaults: CheckboxDefaults = IntelliJTheme.checkboxDefaults,
    colors: CheckboxColors = defaults.colors(),
    shape: Shape = defaults.shape(),
    content: @Composable RowScope.() -> Unit
) = CheckboxImpl(
    state = ToggleableState(checked),
    onClick = {
        onCheckedChange?.invoke(!checked)
    },
    modifier = modifier,
    enabled = enabled,
    isError = isError,
    interactionSource = interactionSource,
    defaults = defaults,
    colors = colors,
    shape = shape,
    content = content
)

@Composable
fun TriStateCheckboxRow(
    state: ToggleableState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isError: Boolean = false,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    defaults: CheckboxDefaults = IntelliJTheme.checkboxDefaults,
    colors: CheckboxColors = defaults.colors(),
    shape: Shape = defaults.shape(),
    content: @Composable RowScope.() -> Unit
) = CheckboxImpl(
    state = state,
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
private fun CheckboxImpl(
    state: ToggleableState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isError: Boolean = false,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    defaults: CheckboxDefaults = IntelliJTheme.checkboxDefaults,
    colors: CheckboxColors = defaults.colors(),
    shape: Shape = defaults.shape(),
    content: (@Composable RowScope.() -> Unit)? = null
) {
    var checkboxState by remember(interactionSource) {
        mutableStateOf(CheckboxState.of(state, enabled = enabled))
    }
    remember(state, isError, enabled) {
        checkboxState = checkboxState.copy(toggle = state, error = isError, enabled = enabled)
    }
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> checkboxState = checkboxState.copy(pressed = true)
                is PressInteraction.Cancel, is PressInteraction.Release -> checkboxState = checkboxState.copy(pressed = false)
                is HoverInteraction.Enter -> checkboxState = checkboxState.copy(hovered = true)
                is HoverInteraction.Exit -> checkboxState = checkboxState.copy(hovered = true)
                is FocusInteraction.Focus -> checkboxState = checkboxState.copy(focused = true)
                is FocusInteraction.Unfocus -> checkboxState = checkboxState.copy(focused = false)
            }
        }
    }

    val wrapperModifier = modifier.triStateToggleable(
        state = state,
        onClick = onClick,
        enabled = enabled,
        role = Role.Checkbox,
        interactionSource = interactionSource,
        indication = null
    )

    val checkBoxModifier = Modifier.size(defaults.width(), defaults.height())
        .background(colors.boxColor(checkboxState).value, shape)
        .border(colors.borderStroke(checkboxState).value, shape)
        .border(colors.haloStroke(checkboxState).value, shape)
        .composed {
            defaults.checkmark(checkboxState).value?.let {
                val colorFilter = ColorFilter.tint(colors.checkmarkColor(checkboxState).value)
                paint(it, colorFilter = colorFilter)
            } ?: this
        }

    if (content == null) {
        Box(wrapperModifier.then(checkBoxModifier))
    } else {
        Row(
            wrapperModifier,
            horizontalArrangement = Arrangement.spacedBy(defaults.contentSpacing()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(checkBoxModifier)
            CompositionLocalProvider(
                LocalTextStyle provides defaults.textStyle(),
                LocalTextColor provides colors.contentColor(checkboxState).value
            ) {
                content()
            }
        }
    }
}

@Immutable
@JvmInline
value class CheckboxState(val state: ULong) {

    @Stable
    val toggle: ToggleableState
        get() = ToggleableState.values()[(state shr toggleStateBitOffset).toInt() and toggleStateMask.toInt()]

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
        toggle: ToggleableState = this.toggle,
        enabled: Boolean = isEnabled,
        focused: Boolean = isFocused,
        error: Boolean = isError,
        pressed: Boolean = isPressed,
        hovered: Boolean = isHovered
    ): CheckboxState = of(
        toggle = toggle,
        enabled = enabled,
        focused = focused,
        error = error,
        pressed = pressed,
        hovered = hovered
    )

    override fun toString(): String =
        "CheckboxState(toggle=$toggle, enabled=$isEnabled, focused=$isFocused, error=$isError, hovered=$isHovered, pressed=$isPressed)"

    companion object {

        private val Enabled = 1UL shl 0
        private val Focused = 1UL shl 1
        private val Error = 1UL shl 2
        private val Hovered = 1UL shl 3
        private val Pressed = 1UL shl 4
        private const val toggleStateBitOffset = 60
        private const val toggleStateMask = 0b11UL

        fun of(
            toggle: ToggleableState,
            enabled: Boolean = true,
            focused: Boolean = false,
            error: Boolean = false,
            pressed: Boolean = false,
            hovered: Boolean = false
        ): CheckboxState {
            return CheckboxState(
                state = (if (enabled) Enabled else 0UL) or
                    (if (focused) Focused else 0UL) or
                    (if (error) Error else 0UL) or
                    (if (pressed) Pressed else 0UL) or
                    (if (hovered) Hovered else 0UL) or
                    (toggle.ordinal.toULong() shl toggleStateBitOffset)
            )
        }
    }
}

@Stable
interface CheckboxColors {

    @Composable
    fun checkmarkColor(state: CheckboxState): State<Color>

    @Composable
    fun boxColor(state: CheckboxState): State<Color>

    @Composable
    fun contentColor(state: CheckboxState): State<Color>

    @Composable
    fun borderStroke(state: CheckboxState): State<Stroke>

    @Composable
    fun haloStroke(state: CheckboxState): State<Stroke>
}

@Stable
interface CheckboxDefaults {

    @Composable
    fun colors(): CheckboxColors

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
    fun checkmark(state: CheckboxState): State<Painter?>
}

fun checkBoxColors(
    checkmarkColor: Color,
    textColor: Color,
    uncheckedBackground: Color,
    uncheckedStroke: Stroke,
    uncheckedFocusedStroke: Stroke,
    uncheckedFocusHoloStroke: Stroke,
    uncheckedErrorHoloStroke: Stroke,
    uncheckedHoveredBackground: Color,
    uncheckedHoveredStroke: Stroke,
    uncheckedDisabledBackground: Color,
    uncheckedDisabledStroke: Stroke,
    checkedBackground: Color,
    checkedStroke: Stroke,
    checkedFocusedStroke: Stroke,
    checkedFocusHoloStroke: Stroke,
    checkedErrorHoloStroke: Stroke,
    checkedHoveredBackground: Color,
    checkedHoveredStroke: Stroke,
    checkedDisabledBackground: Color,
    checkedDisabledStroke: Stroke,
    disabledCheckmarkColor: Color,
    disabledTextColor: Color
): CheckboxColors = DefaultCheckBoxColors(
    checkmarkColor = checkmarkColor,
    textColor = textColor,
    uncheckedBackground = uncheckedBackground,
    uncheckedStroke = uncheckedStroke,
    uncheckedFocusedStroke = uncheckedFocusedStroke,
    uncheckedFocusHoloStroke = uncheckedFocusHoloStroke,
    uncheckedErrorHoloStroke = uncheckedErrorHoloStroke,
    uncheckedHoveredBackground = uncheckedHoveredBackground,
    uncheckedHoveredStroke = uncheckedHoveredStroke,
    uncheckedDisabledBackground = uncheckedDisabledBackground,
    uncheckedDisabledStroke = uncheckedDisabledStroke,
    checkedBackground = checkedBackground,
    checkedStroke = checkedStroke,
    checkedFocusedStroke = checkedFocusedStroke,
    checkedFocusHoloStroke = checkedFocusHoloStroke,
    checkedErrorHoloStroke = checkedErrorHoloStroke,
    checkedHoveredBackground = checkedHoveredBackground,
    checkedHoveredStroke = checkedHoveredStroke,
    checkedDisabledBackground = checkedDisabledBackground,
    checkedDisabledStroke = checkedDisabledStroke,
    disabledCheckmarkColor = disabledCheckmarkColor,
    disabledTextColor = disabledTextColor
)

@Immutable
private data class DefaultCheckBoxColors(
    private val checkmarkColor: Color,
    private val textColor: Color,

    private val uncheckedBackground: Color,
    private val uncheckedStroke: Stroke,
    private val uncheckedFocusedStroke: Stroke,
    private val uncheckedFocusHoloStroke: Stroke,
    private val uncheckedErrorHoloStroke: Stroke,
    private val uncheckedHoveredBackground: Color,
    private val uncheckedHoveredStroke: Stroke,
    private val uncheckedDisabledBackground: Color,
    private val uncheckedDisabledStroke: Stroke,

    private val checkedBackground: Color,
    private val checkedStroke: Stroke,
    private val checkedFocusedStroke: Stroke,
    private val checkedFocusHoloStroke: Stroke,
    private val checkedErrorHoloStroke: Stroke,
    private val checkedHoveredBackground: Color,
    private val checkedHoveredStroke: Stroke,
    private val checkedDisabledBackground: Color,
    private val checkedDisabledStroke: Stroke,

    private val disabledCheckmarkColor: Color,
    private val disabledTextColor: Color
) : CheckboxColors {

    @Composable
    override fun checkmarkColor(state: CheckboxState): State<Color> {
        return rememberUpdatedState(
            when {
                !state.isEnabled -> disabledCheckmarkColor
                else -> checkmarkColor
            }
        )
    }

    @Composable
    override fun borderStroke(state: CheckboxState): State<Stroke> {
        return rememberUpdatedState(
            when {
                !state.isEnabled -> if (state.toggle == ToggleableState.Off) {
                    uncheckedDisabledStroke
                } else {
                    checkedDisabledStroke
                }

                state.isFocused || state.isError -> if (state.toggle == ToggleableState.Off) {
                    uncheckedFocusedStroke
                } else {
                    checkedFocusedStroke
                }

                state.isHovered -> if (state.toggle == ToggleableState.Off) {
                    uncheckedHoveredStroke
                } else {
                    checkedHoveredStroke
                }

                state.toggle == ToggleableState.Off -> uncheckedStroke
                else -> checkedStroke
            }
        )
    }

    @Composable
    override fun haloStroke(state: CheckboxState): State<Stroke> {
        return rememberUpdatedState(
            when {
                !state.isEnabled -> Stroke.None
                state.isError -> if (state.toggle == ToggleableState.Off) {
                    uncheckedErrorHoloStroke
                } else {
                    checkedErrorHoloStroke
                }

                state.isFocused -> if (state.toggle == ToggleableState.Off) {
                    uncheckedFocusHoloStroke
                } else {
                    checkedFocusHoloStroke
                }

                else -> Stroke.None
            }
        )
    }

    @Composable
    override fun boxColor(state: CheckboxState): State<Color> {
        return rememberUpdatedState(
            when {
                !state.isEnabled -> if (state.toggle == ToggleableState.Off) {
                    uncheckedDisabledBackground
                } else {
                    checkedDisabledBackground
                }

                state.isFocused -> if (state.toggle == ToggleableState.Off) {
                    uncheckedBackground
                } else {
                    checkedBackground
                }

                state.isHovered || state.isPressed -> if (state.toggle == ToggleableState.Off) {
                    uncheckedHoveredBackground
                } else {
                    checkedHoveredBackground
                }

                state.toggle == ToggleableState.Off -> uncheckedBackground
                else -> checkedBackground
            }
        )
    }

    @Composable
    override fun contentColor(state: CheckboxState): State<Color> {
        return rememberUpdatedState(
            when {
                !state.isEnabled -> disabledTextColor
                else -> textColor
            }
        )
    }
}

internal val LocalCheckboxDefaults = staticCompositionLocalOf<CheckboxDefaults> {
    error("No CheckboxDefaults provided")
}
