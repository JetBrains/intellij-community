package org.jetbrains.jewel.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import org.jetbrains.jewel.foundation.modifier.onActivated
import org.jetbrains.jewel.foundation.modifier.thenIf
import org.jetbrains.jewel.foundation.state.CommonStateBitMask.Active
import org.jetbrains.jewel.foundation.state.CommonStateBitMask.Enabled
import org.jetbrains.jewel.foundation.state.CommonStateBitMask.Focused
import org.jetbrains.jewel.foundation.state.CommonStateBitMask.Hovered
import org.jetbrains.jewel.foundation.state.CommonStateBitMask.Indeterminate
import org.jetbrains.jewel.foundation.state.CommonStateBitMask.Pressed
import org.jetbrains.jewel.foundation.state.CommonStateBitMask.Selected
import org.jetbrains.jewel.foundation.state.FocusableComponentState
import org.jetbrains.jewel.foundation.state.SelectableComponentState
import org.jetbrains.jewel.foundation.state.ToggleableComponentState
import org.jetbrains.jewel.foundation.state.ToggleableComponentState.Companion.readToggleableState
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.styling.IconButtonStyle
import org.jetbrains.jewel.ui.theme.iconButtonStyle

@Composable
public fun IconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    focusable: Boolean = true,
    style: IconButtonStyle = JewelTheme.iconButtonStyle,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable (BoxScope.(IconButtonState) -> Unit),
) {
    var buttonState by remember(interactionSource) { mutableStateOf(IconButtonState.of(enabled = enabled)) }

    remember(enabled) { buttonState = buttonState.copy(enabled = enabled) }

    LaunchedEffect(key1 = interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> buttonState = buttonState.copy(pressed = true)
                is PressInteraction.Cancel,
                is PressInteraction.Release -> buttonState = buttonState.copy(pressed = false)

                is HoverInteraction.Enter -> buttonState = buttonState.copy(hovered = true)
                is HoverInteraction.Exit -> buttonState = buttonState.copy(hovered = false)
                is FocusInteraction.Focus -> buttonState = buttonState.copy(focused = focusable)
                is FocusInteraction.Unfocus -> buttonState = buttonState.copy(focused = false)
            }
        }
    }

    val shape = RoundedCornerShape(style.metrics.cornerSize)
    val background by style.colors.backgroundFor(buttonState)
    val border by style.colors.borderFor(buttonState)

    Box(
        modifier =
            Modifier.thenIf(!focusable) { focusProperties { canFocus = false } }
                .then(modifier)
                .clickable(
                    onClick = onClick,
                    enabled = enabled,
                    role = Role.Button,
                    interactionSource = interactionSource,
                    indication = null,
                )
                .defaultMinSize(style.metrics.minSize.width, style.metrics.minSize.height)
                .padding(style.metrics.padding)
                .background(background, shape)
                .border(style.metrics.borderWidth, border, shape),
        contentAlignment = Alignment.Center,
        content = { content(buttonState) },
    )
}

@Composable
public fun SelectableIconButton(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    focusable: Boolean = true,
    style: IconButtonStyle = JewelTheme.iconButtonStyle,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable (BoxScope.(SelectableIconButtonState) -> Unit),
) {
    var buttonState by remember(interactionSource) { mutableStateOf(SelectableIconButtonState.of(enabled = enabled)) }

    remember(enabled, selected) { buttonState = buttonState.copy(enabled = enabled, selected = selected) }

    LaunchedEffect(key1 = interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> buttonState = buttonState.copy(pressed = true)
                is PressInteraction.Cancel,
                is PressInteraction.Release -> buttonState = buttonState.copy(pressed = false)

                is HoverInteraction.Enter -> buttonState = buttonState.copy(hovered = true)
                is HoverInteraction.Exit -> buttonState = buttonState.copy(hovered = false)
                is FocusInteraction.Focus -> buttonState = buttonState.copy(focused = focusable)
                is FocusInteraction.Unfocus -> buttonState = buttonState.copy(focused = false)
            }
        }
    }

    val shape = RoundedCornerShape(style.metrics.cornerSize)
    val background by style.colors.selectableBackgroundFor(buttonState)
    val border by style.colors.selectableBorderFor(buttonState)

    Box(
        modifier =
            Modifier.thenIf(!focusable) { focusProperties { canFocus = false } }
                .then(modifier)
                .selectable(
                    onClick = onClick,
                    enabled = enabled,
                    role = Role.RadioButton,
                    interactionSource = interactionSource,
                    indication = null,
                    selected = selected,
                )
                .onActivated(enabled = enabled) { buttonState = buttonState.copy(active = it) }
                .defaultMinSize(style.metrics.minSize.width, style.metrics.minSize.height)
                .padding(style.metrics.padding)
                .background(background, shape)
                .border(style.metrics.borderWidth, border, shape),
        contentAlignment = Alignment.Center,
        content = { content(buttonState) },
    )
}

@Composable
public fun ToggleableIconButton(
    value: Boolean,
    onValueChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    focusable: Boolean = true,
    style: IconButtonStyle = JewelTheme.iconButtonStyle,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable (BoxScope.(ToggleableIconButtonState) -> Unit),
) {
    var buttonState by remember(interactionSource) { mutableStateOf(ToggleableIconButtonState.of(enabled = enabled)) }

    remember(enabled, value) {
        buttonState = buttonState.copy(enabled = enabled, toggleableState = ToggleableState(value))
    }

    LaunchedEffect(key1 = interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> buttonState = buttonState.copy(pressed = true)
                is PressInteraction.Cancel,
                is PressInteraction.Release -> buttonState = buttonState.copy(pressed = false)

                is HoverInteraction.Enter -> buttonState = buttonState.copy(hovered = true)
                is HoverInteraction.Exit -> buttonState = buttonState.copy(hovered = false)
                is FocusInteraction.Focus -> buttonState = buttonState.copy(focused = focusable)
                is FocusInteraction.Unfocus -> buttonState = buttonState.copy(focused = false)
            }
        }
    }

    val shape = RoundedCornerShape(style.metrics.cornerSize)
    val background by style.colors.toggleableBackgroundFor(buttonState)
    val border by style.colors.toggleableBorderFor(buttonState)

    Box(
        modifier =
            Modifier.thenIf(!focusable) { focusProperties { canFocus = false } }
                .then(modifier)
                .toggleable(
                    onValueChange = onValueChange,
                    enabled = enabled,
                    role = Role.Checkbox,
                    interactionSource = interactionSource,
                    indication = null,
                    value = buttonState.toggleableState == ToggleableState.On,
                )
                .onActivated(enabled = enabled) { buttonState = buttonState.copy(active = it) }
                .defaultMinSize(style.metrics.minSize.width, style.metrics.minSize.height)
                .padding(style.metrics.padding)
                .background(background, shape)
                .border(style.metrics.borderWidth, border, shape),
        contentAlignment = Alignment.Center,
        content = { content(buttonState) },
    )
}

@Immutable
@JvmInline
public value class IconButtonState(public val state: ULong) : FocusableComponentState {
    override val isActive: Boolean
        get() = state and Active != 0UL

    override val isEnabled: Boolean
        get() = state and Enabled != 0UL

    override val isFocused: Boolean
        get() = state and Focused != 0UL

    override val isHovered: Boolean
        get() = state and Hovered != 0UL

    override val isPressed: Boolean
        get() = state and Pressed != 0UL

    public fun copy(
        enabled: Boolean = isEnabled,
        focused: Boolean = isFocused,
        pressed: Boolean = isPressed,
        hovered: Boolean = isHovered,
        active: Boolean = isActive,
    ): IconButtonState = of(enabled = enabled, focused = focused, pressed = pressed, hovered = hovered, active = active)

    override fun toString(): String =
        "${javaClass.simpleName}(isEnabled=$isEnabled, isFocused=$isFocused, isHovered=$isHovered, " +
            "isPressed=$isPressed, isActive=$isActive)"

    public companion object {
        public fun of(
            enabled: Boolean = true,
            focused: Boolean = false,
            pressed: Boolean = false,
            hovered: Boolean = false,
            active: Boolean = false,
        ): IconButtonState =
            IconButtonState(
                (if (enabled) Enabled else 0UL) or
                    (if (focused) Focused else 0UL) or
                    (if (hovered) Hovered else 0UL) or
                    (if (pressed) Pressed else 0UL) or
                    (if (active) Active else 0UL)
            )
    }
}

@Immutable
@JvmInline
public value class ToggleableIconButtonState(public val state: ULong) :
    FocusableComponentState, ToggleableComponentState {
    override val toggleableState: ToggleableState
        get() = state.readToggleableState()

    override val isActive: Boolean
        get() = state and Active != 0UL

    override val isEnabled: Boolean
        get() = state and Enabled != 0UL

    override val isFocused: Boolean
        get() = state and Focused != 0UL

    override val isHovered: Boolean
        get() = state and Hovered != 0UL

    override val isPressed: Boolean
        get() = state and Pressed != 0UL

    public fun copy(
        enabled: Boolean = isEnabled,
        toggleableState: ToggleableState = this.toggleableState,
        focused: Boolean = isFocused,
        pressed: Boolean = isPressed,
        hovered: Boolean = isHovered,
        active: Boolean = isActive,
    ): ToggleableIconButtonState =
        of(
            enabled = enabled,
            toggleableState = toggleableState,
            focused = focused,
            pressed = pressed,
            hovered = hovered,
            active = active,
        )

    override fun toString(): String =
        "${javaClass.simpleName}(isEnabled=$isEnabled, isFocused=$isFocused, isHovered=$isHovered, " +
            "isPressed=$isPressed, isActive=$isActive, toggleableState=$toggleableState)"

    public companion object {
        public fun of(
            enabled: Boolean = true,
            toggleableState: ToggleableState = ToggleableState.Off,
            focused: Boolean = false,
            pressed: Boolean = false,
            hovered: Boolean = false,
            active: Boolean = false,
        ): ToggleableIconButtonState =
            ToggleableIconButtonState(
                (if (enabled) Enabled else 0UL) or
                    (if (focused) Focused else 0UL) or
                    (if (hovered) Hovered else 0UL) or
                    (if (pressed) Pressed else 0UL) or
                    (if (active) Active else 0UL) or
                    (if (toggleableState != ToggleableState.Off) Selected else 0UL) or
                    (if (toggleableState == ToggleableState.Indeterminate) Indeterminate else 0UL)
            )
    }
}

@Immutable
@JvmInline
public value class SelectableIconButtonState(public val state: ULong) :
    FocusableComponentState, SelectableComponentState {
    override val isSelected: Boolean
        get() = state and Selected != 0UL

    override val isActive: Boolean
        get() = state and Active != 0UL

    override val isEnabled: Boolean
        get() = state and Enabled != 0UL

    override val isFocused: Boolean
        get() = state and Focused != 0UL

    override val isHovered: Boolean
        get() = state and Hovered != 0UL

    override val isPressed: Boolean
        get() = state and Pressed != 0UL

    public fun copy(
        enabled: Boolean = isEnabled,
        selected: Boolean = isSelected,
        focused: Boolean = isFocused,
        pressed: Boolean = isPressed,
        hovered: Boolean = isHovered,
        active: Boolean = isActive,
    ): SelectableIconButtonState =
        of(
            enabled = enabled,
            selected = selected,
            focused = focused,
            pressed = pressed,
            hovered = hovered,
            active = active,
        )

    override fun toString(): String =
        "${javaClass.simpleName}(isEnabled=$isEnabled, isSelected=$isSelected, " +
            "isFocused=$isFocused, isHovered=$isHovered, isPressed=$isPressed, " +
            "isActive=$isActive)"

    public companion object {
        public fun of(
            enabled: Boolean = true,
            selected: Boolean = false,
            focused: Boolean = false,
            pressed: Boolean = false,
            hovered: Boolean = false,
            active: Boolean = false,
        ): SelectableIconButtonState =
            SelectableIconButtonState(
                (if (enabled) Enabled else 0UL) or
                    (if (selected) Selected else 0UL) or
                    (if (focused) Focused else 0UL) or
                    (if (hovered) Hovered else 0UL) or
                    (if (pressed) Pressed else 0UL) or
                    (if (active) Active else 0UL)
            )
    }
}
