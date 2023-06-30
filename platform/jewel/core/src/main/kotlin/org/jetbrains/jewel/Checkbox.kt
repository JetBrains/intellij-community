package org.jetbrains.jewel

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.ResourceLoader
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.TextStyle
import org.jetbrains.jewel.CommonStateBitMask.Enabled
import org.jetbrains.jewel.CommonStateBitMask.Error
import org.jetbrains.jewel.CommonStateBitMask.Focused
import org.jetbrains.jewel.CommonStateBitMask.Hovered
import org.jetbrains.jewel.CommonStateBitMask.Pressed
import org.jetbrains.jewel.CommonStateBitMask.Warning
import org.jetbrains.jewel.styling.CheckboxColors
import org.jetbrains.jewel.styling.CheckboxIcons
import org.jetbrains.jewel.styling.CheckboxMetrics
import org.jetbrains.jewel.styling.LocalCheckboxStyle

@Composable
fun Checkbox(
    checked: Boolean,
    resourceLoader: ResourceLoader,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isError: Boolean = false,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    colors: CheckboxColors = LocalCheckboxStyle.current.colors,
    metrics: CheckboxMetrics = LocalCheckboxStyle.current.metrics,
    icons: CheckboxIcons = LocalCheckboxStyle.current.icons
) = CheckboxImpl(
    state = ToggleableState(checked),
    onClick = {
        onCheckedChange.invoke(!checked)
    },
    modifier = modifier,
    enabled = enabled,
    isError = isError,
    interactionSource = interactionSource,
    colors = colors,
    metrics = metrics,
    icons = icons,
    textStyle = IntelliJTheme.defaultTextStyle,
    resourceLoader = resourceLoader,
    content = null
)

@Composable
fun TriStateCheckbox(
    state: ToggleableState,
    resourceLoader: ResourceLoader,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isError: Boolean = false,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    colors: CheckboxColors = LocalCheckboxStyle.current.colors,
    metrics: CheckboxMetrics = LocalCheckboxStyle.current.metrics,
    icons: CheckboxIcons = LocalCheckboxStyle.current.icons
) = CheckboxImpl(
    state = state,
    onClick = onClick,
    modifier = modifier,
    enabled = enabled,
    isError = isError,
    interactionSource = interactionSource,
    colors = colors,
    metrics = metrics,
    icons = icons,
    textStyle = IntelliJTheme.defaultTextStyle,
    resourceLoader = resourceLoader,
    content = null
)

@Composable
fun TriStateCheckboxRow(
    text: String,
    state: ToggleableState,
    resourceLoader: ResourceLoader,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isError: Boolean = false,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    colors: CheckboxColors = LocalCheckboxStyle.current.colors,
    metrics: CheckboxMetrics = LocalCheckboxStyle.current.metrics,
    icons: CheckboxIcons = LocalCheckboxStyle.current.icons,
    textStyle: TextStyle = LocalTextStyle.current
) = CheckboxImpl(
    state = state,
    onClick = onClick,
    modifier = modifier,
    enabled = enabled,
    isError = isError,
    interactionSource = interactionSource,
    colors = colors,
    metrics = metrics,
    icons = icons,
    resourceLoader = resourceLoader,
    textStyle = textStyle
) {
    Text(text)
}

@Composable
fun CheckboxRow(
    text: String,
    checked: Boolean,
    resourceLoader: ResourceLoader,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isError: Boolean = false,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    colors: CheckboxColors = LocalCheckboxStyle.current.colors,
    metrics: CheckboxMetrics = LocalCheckboxStyle.current.metrics,
    icons: CheckboxIcons = LocalCheckboxStyle.current.icons,
    textStyle: TextStyle = LocalTextStyle.current
) = CheckboxImpl(
    state = ToggleableState(checked),
    onClick = {
        onCheckedChange?.invoke(!checked)
    },
    modifier = modifier,
    enabled = enabled,
    isError = isError,
    interactionSource = interactionSource,
    colors = colors,
    metrics = metrics,
    icons = icons,
    resourceLoader = resourceLoader,
    textStyle = textStyle
) {
    Text(text)
}

@Composable
fun CheckboxRow(
    checked: Boolean,
    resourceLoader: ResourceLoader,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isError: Boolean = false,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    colors: CheckboxColors = LocalCheckboxStyle.current.colors,
    metrics: CheckboxMetrics = LocalCheckboxStyle.current.metrics,
    icons: CheckboxIcons = LocalCheckboxStyle.current.icons,
    textStyle: TextStyle = LocalTextStyle.current,
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
    colors = colors,
    metrics = metrics,
    icons = icons,
    resourceLoader = resourceLoader,
    textStyle = textStyle,
    content = content
)

@Composable
fun TriStateCheckboxRow(
    state: ToggleableState,
    resourceLoader: ResourceLoader,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isError: Boolean = false,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    colors: CheckboxColors = LocalCheckboxStyle.current.colors,
    metrics: CheckboxMetrics = LocalCheckboxStyle.current.metrics,
    icons: CheckboxIcons = LocalCheckboxStyle.current.icons,
    textStyle: TextStyle = LocalTextStyle.current,
    content: @Composable RowScope.() -> Unit
) = CheckboxImpl(
    state = state,
    onClick = onClick,
    modifier = modifier,
    enabled = enabled,
    isError = isError,
    interactionSource = interactionSource,
    colors = colors,
    metrics = metrics,
    icons = icons,
    resourceLoader = resourceLoader,
    textStyle = textStyle,
    content = content
)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun CheckboxImpl(
    state: ToggleableState,
    onClick: () -> Unit,
    colors: CheckboxColors,
    metrics: CheckboxMetrics,
    icons: CheckboxIcons,
    resourceLoader: ResourceLoader,
    modifier: Modifier,
    enabled: Boolean,
    isError: Boolean,
    interactionSource: MutableInteractionSource,
    textStyle: TextStyle,
    content: (@Composable RowScope.() -> Unit)?
) {
    var checkboxState by remember(interactionSource) {
        mutableStateOf(CheckboxState.of(state, enabled = enabled))
    }
    remember(state, isError, enabled) {
        checkboxState = checkboxState.copy(toggleableState = state, error = isError, enabled = enabled)
    }
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> checkboxState = checkboxState.copy(pressed = true)
                is PressInteraction.Cancel, is PressInteraction.Release ->
                    checkboxState =
                        checkboxState.copy(pressed = false)

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

    val checkBoxModifier = Modifier.size(metrics.checkboxSize)
        .outline(checkboxState, outlineShape = RoundedCornerShape(metrics.checkboxCornerSize))
    val checkboxPainter by icons.getPainter(checkboxState, resourceLoader)

    if (content == null) {
        CheckBoxImage(wrapperModifier, checkboxPainter, checkBoxModifier)
    } else {
        Row(
            wrapperModifier,
            horizontalArrangement = Arrangement.spacedBy(metrics.iconContentGap),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CheckBoxImage(Modifier, checkboxPainter, checkBoxModifier)

            CompositionLocalProvider(
                LocalTextStyle provides textStyle,
                LocalContentColor provides colors.contentFor(checkboxState).value
            ) {
                content()
            }
        }
    }
}

@Composable
private fun CheckBoxImage(outerModifier: Modifier, checkboxPainter: Painter, checkBoxModifier: Modifier) {
    Box(outerModifier) {
        Image(checkboxPainter, contentDescription = null, modifier = checkBoxModifier)
    }
}

@Immutable
@JvmInline
value class CheckboxState(private val state: ULong) : StateWithOutline {

    @Stable
    val toggleableState: ToggleableState
        get() = state.readToggleableState()

    @Stable
    override val isEnabled: Boolean
        get() = state and Enabled != 0UL

    @Stable
    override val isFocused: Boolean
        get() = state and Focused != 0UL

    @Stable
    override val isError: Boolean
        get() = state and Error != 0UL

    @Stable
    override val isWarning: Boolean
        get() = state and Warning != 0UL

    @Stable
    override val isHovered: Boolean
        get() = state and Hovered != 0UL

    @Stable
    override val isPressed: Boolean
        get() = state and Pressed != 0UL

    fun copy(
        toggleableState: ToggleableState = this.toggleableState,
        enabled: Boolean = isEnabled,
        focused: Boolean = isFocused,
        error: Boolean = isError,
        pressed: Boolean = isPressed,
        hovered: Boolean = isHovered,
        warning: Boolean = isWarning
    ) = of(
        toggleableState = toggleableState,
        enabled = enabled,
        focused = focused,
        error = error,
        pressed = pressed,
        hovered = hovered,
        warning = warning
    )

    override fun toString() =
        "${javaClass.simpleName}(toggleableState=$toggleableState, isEnabled=$isEnabled, isFocused=$isFocused, " +
            "isError=$isError, isWarning=$isWarning, isHovered=$isHovered, isPressed=$isPressed)"

    companion object {

        private const val TOGGLE_STATE_BIT_OFFSET = CommonStateBitMask.FIRST_AVAILABLE_OFFSET
        private const val TOGGLE_STATE_MASK = 0b11UL
        private const val TOGGLE_STATE_OFF = 0b00UL
        private const val TOGGLE_STATE_ON = 0b01UL
        private const val TOGGLE_STATE_INDETERMINATE = 0b11UL

        fun of(
            toggleableState: ToggleableState,
            enabled: Boolean = true,
            focused: Boolean = false,
            error: Boolean = false,
            pressed: Boolean = false,
            hovered: Boolean = false,
            warning: Boolean = false
        ) = CheckboxState(
            state = (if (enabled) Enabled else 0UL) or
                (if (focused) Focused else 0UL) or
                (if (error) Error else 0UL) or
                (if (hovered) Hovered else 0UL) or
                (if (pressed) Pressed else 0UL) or
                (if (warning) Warning else 0UL) or
                (toggleableState.toBitMask() shl TOGGLE_STATE_BIT_OFFSET)
        )

        private fun ToggleableState.toBitMask() = when (this) {
            ToggleableState.On -> TOGGLE_STATE_ON
            ToggleableState.Off -> TOGGLE_STATE_OFF
            ToggleableState.Indeterminate -> TOGGLE_STATE_INDETERMINATE
        }

        private fun ULong.readToggleableState(): ToggleableState {
            val bits = (this shr TOGGLE_STATE_BIT_OFFSET) and TOGGLE_STATE_MASK

            return when (bits) {
                TOGGLE_STATE_ON -> ToggleableState.On
                TOGGLE_STATE_OFF -> ToggleableState.Off
                TOGGLE_STATE_INDETERMINATE -> ToggleableState.Indeterminate
                else -> error("Invalid toggleable state: $bits (0x${bits.toString(2)})")
            }
        }
    }
}
