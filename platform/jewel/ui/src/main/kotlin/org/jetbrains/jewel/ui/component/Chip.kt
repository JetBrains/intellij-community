package org.jetbrains.jewel.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.semantics.Role
import org.jetbrains.jewel.foundation.Stroke
import org.jetbrains.jewel.foundation.modifier.border
import org.jetbrains.jewel.foundation.state.CommonStateBitMask.Active
import org.jetbrains.jewel.foundation.state.CommonStateBitMask.Enabled
import org.jetbrains.jewel.foundation.state.CommonStateBitMask.Focused
import org.jetbrains.jewel.foundation.state.CommonStateBitMask.Hovered
import org.jetbrains.jewel.foundation.state.CommonStateBitMask.Pressed
import org.jetbrains.jewel.foundation.state.CommonStateBitMask.Selected
import org.jetbrains.jewel.foundation.state.FocusableComponentState
import org.jetbrains.jewel.foundation.state.SelectableComponentState
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.foundation.theme.LocalContentColor
import org.jetbrains.jewel.foundation.theme.LocalTextStyle
import org.jetbrains.jewel.ui.component.styling.ChipStyle
import org.jetbrains.jewel.ui.focusOutline
import org.jetbrains.jewel.ui.theme.chipStyle
import org.jetbrains.jewel.ui.util.thenIf

@Composable
public fun Chip(
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    enabled: Boolean = true,
    selected: Boolean = false,
    style: ChipStyle = JewelTheme.chipStyle,
    onClick: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    ChipImpl(
        interactionSource = interactionSource,
        enabled = enabled,
        selected = selected,
        style = style,
        modifier =
            modifier.clickable(
                onClick = onClick,
                enabled = enabled,
                role = Role.Button,
                interactionSource = interactionSource,
                indication = null,
            ),
        content = content,
    )
}

@Composable
public fun ToggleableChip(
    checked: Boolean,
    onClick: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    enabled: Boolean = true,
    style: ChipStyle = JewelTheme.chipStyle,
    content: @Composable () -> Unit,
) {
    ChipImpl(
        interactionSource = interactionSource,
        enabled = enabled,
        selected = checked,
        style = style,
        modifier =
            modifier.toggleable(
                onValueChange = onClick,
                enabled = enabled,
                role = Role.Checkbox,
                interactionSource = interactionSource,
                indication = null,
                value = checked,
            ),
        content = content,
    )
}

@Composable
public fun RadioButtonChip(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    enabled: Boolean = true,
    style: ChipStyle = JewelTheme.chipStyle,
    content: @Composable () -> Unit,
) {
    ChipImpl(
        interactionSource,
        enabled,
        selected,
        style,
        modifier =
            modifier.selectable(
                onClick = onClick,
                enabled = enabled,
                role = Role.RadioButton,
                interactionSource = interactionSource,
                indication = null,
                selected = selected,
            ),
        content,
    )
}

@Composable
private fun ChipImpl(
    interactionSource: MutableInteractionSource,
    enabled: Boolean,
    selected: Boolean,
    style: ChipStyle,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    var chipState by
        remember(interactionSource) { mutableStateOf(ChipState.of(enabled = enabled, selected = selected)) }

    remember(enabled, selected) { chipState = chipState.copy(enabled = enabled, selected = selected) }

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> chipState = chipState.copy(pressed = true)
                is PressInteraction.Cancel,
                is PressInteraction.Release -> chipState = chipState.copy(pressed = false)
                is HoverInteraction.Enter -> chipState = chipState.copy(hovered = true)
                is HoverInteraction.Exit -> chipState = chipState.copy(hovered = false)
                is FocusInteraction.Focus -> chipState = chipState.copy(focused = true)
                is FocusInteraction.Unfocus -> chipState = chipState.copy(focused = false)
            }
        }
    }

    val shape = RoundedCornerShape(style.metrics.cornerSize)
    val colors = style.colors
    val borderColor by colors.borderFor(chipState)

    val borderWidth =
        if (chipState.isSelected) {
            style.metrics.borderWidthSelected
        } else {
            style.metrics.borderWidth
        }

    Row(
        modifier =
            modifier
                .background(colors.backgroundFor(chipState).value, shape)
                .thenIf(!chipState.isFocused) { border(Stroke.Alignment.Center, borderWidth, borderColor, shape) }
                .focusOutline(chipState, shape)
                .padding(style.metrics.padding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        val resolvedContentColor = colors.contentFor(state = chipState).value.takeOrElse { LocalContentColor.current }

        CompositionLocalProvider(
            LocalContentColor provides resolvedContentColor,
            LocalTextStyle provides LocalTextStyle.current.copy(color = colors.contentFor(chipState).value),
        ) {
            content()
        }
    }
}

@Immutable
@JvmInline
public value class ChipState(public val state: ULong) : FocusableComponentState, SelectableComponentState {
    override val isActive: Boolean
        get() = state and Active != 0UL

    override val isEnabled: Boolean
        get() = state and Enabled != 0UL

    override val isFocused: Boolean
        get() = state and Focused != 0UL

    override val isSelected: Boolean
        get() = state and Selected != 0UL

    override val isHovered: Boolean
        get() = state and Hovered != 0UL

    override val isPressed: Boolean
        get() = state and Pressed != 0UL

    public fun copy(
        enabled: Boolean = isEnabled,
        focused: Boolean = isFocused,
        selected: Boolean = isSelected,
        pressed: Boolean = isPressed,
        hovered: Boolean = isHovered,
        active: Boolean = isActive,
    ): ChipState =
        of(
            enabled = enabled,
            focused = focused,
            pressed = pressed,
            hovered = hovered,
            active = active,
            selected = selected,
        )

    override fun toString(): String =
        "ChipState(isEnabled=$isEnabled, isFocused=$isFocused, isSelected=$isSelected, " +
            "isHovered=$isHovered, isPressed=$isPressed, isActive=$isActive)"

    public companion object {
        public fun of(
            enabled: Boolean = true,
            focused: Boolean = false,
            selected: Boolean = false,
            pressed: Boolean = false,
            hovered: Boolean = false,
            active: Boolean = false,
        ): ChipState =
            ChipState(
                (if (enabled) Enabled else 0UL) or
                    (if (focused) Focused else 0UL) or
                    (if (selected) Selected else 0UL) or
                    (if (hovered) Hovered else 0UL) or
                    (if (pressed) Pressed else 0UL) or
                    (if (active) Active else 0UL)
            )
    }
}
