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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import org.jetbrains.jewel.foundation.modifier.onActivated
import org.jetbrains.jewel.foundation.state.CommonStateBitMask
import org.jetbrains.jewel.foundation.state.FocusableComponentState
import org.jetbrains.jewel.foundation.state.SelectableComponentState
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.NoIndication
import org.jetbrains.jewel.ui.component.styling.IconButtonStyle
import org.jetbrains.jewel.ui.theme.iconButtonStyle

@Composable
public fun IconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: IconButtonStyle = JewelTheme.iconButtonStyle,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable (BoxScope.(IconButtonState) -> Unit),
) {
    val buttonState = remember(interactionSource) { mutableStateOf(IconButtonState.of(enabled = enabled)) }

    remember(enabled) { buttonState.value = buttonState.value.copy(enabled = enabled) }

    IconButtonImpl(
        state = buttonState,
        modifier = modifier.clickable(
            onClick = onClick,
            enabled = enabled,
            role = Role.Button,
            interactionSource = interactionSource,
            indication = NoIndication,
        ),
        style = style,
        interactionSource = interactionSource,
        content = content,
    )
}

@Composable
public fun SelectableIconButton(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: IconButtonStyle = JewelTheme.iconButtonStyle,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable (BoxScope.(IconButtonState) -> Unit),
) {
    val buttonState = remember(interactionSource) {
        mutableStateOf(IconButtonState.of(enabled = enabled))
    }

    remember(enabled, selected) {
        buttonState.value = buttonState.value.copy(enabled = enabled, selected = selected)
    }

    IconButtonImpl(
        state = buttonState,
        modifier = modifier
            .selectable(
                onClick = onClick,
                enabled = enabled,
                role = Role.RadioButton,
                interactionSource = interactionSource,
                indication = null,
                selected = selected,
            )
            .onActivated(enabled = enabled) {
                buttonState.value = buttonState.value.copy(active = it)
            },
        style = style,
        interactionSource = interactionSource,
        content = content,
    )
}

@Composable
private fun IconButtonImpl(
    state: MutableState<IconButtonState>,
    modifier: Modifier,
    style: IconButtonStyle,
    interactionSource: MutableInteractionSource,
    content: @Composable (BoxScope.(IconButtonState) -> Unit),
) {
    var buttonState by state

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> buttonState = buttonState.copy(pressed = true)
                is PressInteraction.Cancel,
                is PressInteraction.Release,
                -> buttonState = buttonState.copy(pressed = false)

                is HoverInteraction.Enter -> buttonState = buttonState.copy(hovered = true)
                is HoverInteraction.Exit -> buttonState = buttonState.copy(hovered = false)
                is FocusInteraction.Focus -> buttonState = buttonState.copy(focused = true)
                is FocusInteraction.Unfocus -> buttonState = buttonState.copy(focused = false)
            }
        }
    }
    val shape = RoundedCornerShape(style.metrics.cornerSize)
    val background by style.colors.backgroundFor(buttonState)
    val border by style.colors.borderFor(buttonState)
    Box(
        modifier = modifier.defaultMinSize(style.metrics.minSize.width, style.metrics.minSize.height)
            .padding(style.metrics.padding)
            .background(background, shape)
            .border(style.metrics.borderWidth, border, shape),
        contentAlignment = Alignment.Center,
        content = { content(buttonState) },
    )
}

@Immutable
@JvmInline
public value class IconButtonState(public val state: ULong) : FocusableComponentState, SelectableComponentState {

    override val isSelected: Boolean
        get() = state and CommonStateBitMask.Selected != 0UL

    override val isActive: Boolean
        get() = state and CommonStateBitMask.Active != 0UL

    override val isEnabled: Boolean
        get() = state and CommonStateBitMask.Enabled != 0UL

    override val isFocused: Boolean
        get() = state and CommonStateBitMask.Focused != 0UL

    override val isHovered: Boolean
        get() = state and CommonStateBitMask.Hovered != 0UL

    override val isPressed: Boolean
        get() = state and CommonStateBitMask.Pressed != 0UL

    public fun copy(
        enabled: Boolean = isEnabled,
        selected: Boolean = isSelected,
        focused: Boolean = isFocused,
        pressed: Boolean = isPressed,
        hovered: Boolean = isHovered,
        active: Boolean = isActive,
    ): IconButtonState =
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
        ): IconButtonState =
            IconButtonState(
                (if (enabled) CommonStateBitMask.Enabled else 0UL) or
                    (if (selected) CommonStateBitMask.Selected else 0UL) or
                    (if (focused) CommonStateBitMask.Focused else 0UL) or
                    (if (hovered) CommonStateBitMask.Hovered else 0UL) or
                    (if (pressed) CommonStateBitMask.Pressed else 0UL) or
                    (if (active) CommonStateBitMask.Active else 0UL),
            )
    }
}
