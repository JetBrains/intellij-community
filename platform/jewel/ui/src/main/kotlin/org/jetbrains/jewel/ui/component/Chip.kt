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
import org.jetbrains.jewel.foundation.modifier.thenIf
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

/**
 * A lightweight, button-like component used to represent actions, attributes, or filters.
 *
 * The `Chip` component is a compact, clickable element that provides visual feedback for various interaction states
 * (hover, focus, press, etc.). It is versatile and customizable, allowing developers to define its style, behavior, and
 * content.
 *
 * **Guidelines:** Reference Compose Chips for usage suggestions:
 * [Compose Chips Documentation](https://developer.android.com/develop/ui/compose/components/chip)
 *
 * **Usage example:**
 *
 * ```kotlin
 * var clicks by remember { mutableStateOf(0) }
 * Chip(
 *     onClick = { clicks++ },
 *     enabled = true
 * ) {
 *     Text("Clicked: $clicks times!")
 * }
 * ```
 *
 * **Key Features:**
 * - Small footprint, ideal for short text, icons, or a combination.
 * - Fully customizable using [ChipStyle], enabling tailored shapes, paddings, and colors.
 * - Adapts dynamically to user interactions with built-in state management.
 *
 * **When to use:**
 * - To suggest or apply filters (e.g., user-selected tags).
 * - To surface commonly used, secondary actions.
 * - To display compact attributes or classifications.
 *
 * @param modifier Modifier to customize the layout and visual appearance.
 * @param interactionSource Custom [MutableInteractionSource] for observing chip interaction events.
 * @param enabled Controls interactivity. When `false`, the chip is non-clickable and rendered visually as disabled.
 * @param selected Tracks the visual and semantic selection state of the chip.
 * @param style Defines the visual styling of the chip (via [ChipStyle]).
 * @param onClick Action to perform when the chip is clicked.
 * @param content The composable content displayed inside the chip (e.g., text, icons, or a combination).
 */
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

/**
 * A toggleable version of [Chip], representing a switchable on/off state.
 *
 * `ToggleableChip` provides a compact UI element that can toggle between two states: checked and unchecked. This
 * component is suitable for enabling/disabling an option or toggling a feature.
 *
 * **Usage example:**
 *
 * ```kotlin
 * var isChecked by remember { mutableStateOf(false) }
 * ToggleableChip(
 *     checked = isChecked,
 *     onClick = { isChecked = it },
 *     enabled = true
 * ) {
 *     Text(if (isChecked) "Enabled" else "Disabled")
 * }
 * ```
 *
 * **Key Features:**
 * - Represents a binary (checked/unchecked) state, similar to a checkbox.
 * - Fully customizable using [ChipStyle], enabling tailored shapes, paddings, and colors.
 * - Automatically adapts visual appearance based on toggle states.
 *
 * **When to use:**
 * - To create toggleable filters or options.
 * - To represent binary states that persist or control a feature.
 * - As a compact alternative to a checkbox.
 *
 * **State Management:** The `checked` parameter controls the current state of the chip, while `onClick` defines the
 * behavior when the chip is toggled.
 *
 * @param checked Indicates whether the chip is checked (true) or unchecked (false).
 * @param onClick Action to perform when the chip is toggled. The new toggle state is passed to this lambda.
 * @param modifier Modifier to customize chip layout and visuals.
 * @param interactionSource Custom [MutableInteractionSource] for observing chip interaction events.
 * @param enabled Controls interactivity. When `false`, the chip cannot be toggled.
 * @param style Defines the visual styling of the chip (via [ChipStyle]).
 * @param content The composable content displayed inside the chip (e.g., text, icons, or a combination).
 */
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

/**
 * A chip component designed for mutually exclusive selection within a group.
 *
 * `RadioButtonChip` provides a compact UI element for representing an option in a single-selection group. When
 * selected, it automatically deselects other chips in the group.
 *
 * **Usage example:**
 *
 * ```kotlin
 * var selectedOption by remember { mutableStateOf(0) }
 * Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
 *     RadioButtonChip(
 *         selected = selectedOption == 0,
 *         onClick = { selectedOption = 0 }
 *     ) { Text("Option 1") }
 *     RadioButtonChip(
 *         selected = selectedOption == 1,
 *         onClick = { selectedOption = 1 }
 *     ) { Text("Option 2") }
 *     RadioButtonChip(
 *         selected = selectedOption == 2,
 *         onClick = { selectedOption = 2 }
 *     ) { Text("Option 3") }
 * }
 * ```
 *
 * **Key Features:**
 * - Represents mutually exclusive states in a single-selection group.
 * - Fully customizable using [ChipStyle], enabling tailored shapes, paddings, and colors.
 * - Automatically adapts visual appearance depending on whether the chip is selected or not.
 *
 * **When to use:**
 * - To represent options in a single-selection choice group (e.g., forms, filters).
 * - For small, space-efficient alternatives to radio buttons.
 *
 * **State Management:** The `selected` parameter controls whether the chip is active, while `onClick` handles selection
 * actions.
 *
 * @param selected Indicates whether the chip is selected in the group.
 * @param onClick Action to perform when the chip is selected.
 * @param modifier Modifier to customize chip layout and visuals.
 * @param interactionSource Custom [MutableInteractionSource] for observing chip interaction events.
 * @param enabled Controls interactivity. When `false`, the chip cannot be selected or clicked.
 * @param style Defines the visual styling of the chip (via [ChipStyle]).
 * @param content The composable content displayed inside the chip (e.g., text, icons, or a combination).
 */
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
    modifier: Modifier = Modifier,
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
