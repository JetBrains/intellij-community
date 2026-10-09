// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
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
import org.jetbrains.jewel.foundation.state.ToggleableComponentState
import org.jetbrains.jewel.foundation.state.ToggleableComponentState.Companion.readToggleableState
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.styling.OnOffButtonStyle
import org.jetbrains.jewel.ui.theme.onOffButtonStyle

/**
 * An on/off toggle: a pill-shaped track with a notch that sits at the end when [checked] and at the start otherwise.
 *
 * The component reserves room around the track for its focus ring, so gaining focus never changes its size.
 *
 * @param checked Whether the button is on.
 * @param onCheckedChange Called with the new value when the user toggles the button.
 * @param modifier The modifier to apply to this layout.
 * @param enabled Whether the button can be interacted with. A disabled button can't be focused.
 * @param interactionSource The [MutableInteractionSource] to observe and emit interactions for this button.
 * @param style The [OnOffButtonStyle] that defines the colors and metrics of the button.
 */
@Composable
public fun OnOffButton(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    style: OnOffButtonStyle = JewelTheme.onOffButtonStyle,
) {
    var buttonState by remember { mutableStateOf(OnOffButtonState.of(checked = checked, enabled = enabled)) }

    remember(checked, enabled) { buttonState = buttonState.copy(checked = checked, enabled = enabled) }

    val swingCompatMode = JewelTheme.isSwingCompatMode
    LaunchedEffect(interactionSource, swingCompatMode) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> buttonState = buttonState.copy(pressed = !swingCompatMode)
                is PressInteraction.Cancel,
                is PressInteraction.Release -> buttonState = buttonState.copy(pressed = false)

                is HoverInteraction.Enter -> buttonState = buttonState.copy(hovered = !swingCompatMode)
                is HoverInteraction.Exit -> buttonState = buttonState.copy(hovered = false)
                is FocusInteraction.Focus -> buttonState = buttonState.copy(focused = true)
                is FocusInteraction.Unfocus -> buttonState = buttonState.copy(focused = false)
            }
        }
    }

    val colors = style.colors
    val metrics = style.metrics

    val background by colors.backgroundFor(buttonState)
    val border by colors.borderFor(buttonState)
    val notchColor by colors.notchFor(buttonState)

    val trackShape = RoundedCornerShape(metrics.trackCornerSize)
    val notchSize = if (checked) metrics.notchSizeOn else metrics.notchSizeOff
    val notchShape = RoundedCornerShape(if (checked) metrics.notchCornerSizeOn else metrics.notchCornerSizeOff)
    // Keeps the notch as far from the track's end as it is from the track's top and bottom
    val notchPadding = ((metrics.trackSize.height - notchSize.height) / 2).coerceAtLeast(0.dp)

    Box(
        modifier =
            modifier
                .toggleable(
                    value = checked,
                    enabled = enabled,
                    role = Role.Switch,
                    interactionSource = interactionSource,
                    indication = null,
                    onValueChange = onCheckedChange,
                )
                .padding(metrics.focusOutlineWidth + metrics.focusOutlineExpand)
    ) {
        Box(
            modifier =
                Modifier.size(metrics.trackSize)
                    .thenIf(buttonState.isFocused && buttonState.isEnabled) {
                        border(
                            alignment = Stroke.Alignment.Outside,
                            width = metrics.focusOutlineWidth,
                            color = colors.borderFocused,
                            shape = trackShape,
                            expand = metrics.focusOutlineExpand,
                        )
                    }
                    .background(background, trackShape)
                    .border(Stroke.Alignment.Inside, metrics.borderWidth, border, trackShape),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(
                Modifier.padding(horizontal = notchPadding)
                    .size(notchSize)
                    .then(
                        if (checked) {
                            Modifier.background(notchColor, notchShape)
                        } else {
                            Modifier.border(
                                Stroke.Alignment.Inside,
                                metrics.notchStrokeWidthOff,
                                notchColor,
                                notchShape,
                            )
                        }
                    )
            )
        }
    }
}

/** Encodes the checked, enabled, focused, hovered, pressed, and active states of an [OnOffButton] as a bit mask. */
@Immutable
@JvmInline
public value class OnOffButtonState(private val state: ULong) : ToggleableComponentState, FocusableComponentState {
    override val toggleableState: ToggleableState
        get() = state.readToggleableState()

    override val isEnabled: Boolean
        get() = state and Enabled != 0UL

    override val isActive: Boolean
        get() = state and Active != 0UL

    override val isFocused: Boolean
        get() = state and Focused != 0UL

    override val isHovered: Boolean
        get() = state and Hovered != 0UL

    override val isPressed: Boolean
        get() = state and Pressed != 0UL

    /** Returns a copy of this [OnOffButtonState] with the given fields replaced by their new values. */
    public fun copy(
        checked: Boolean = isSelected,
        enabled: Boolean = isEnabled,
        focused: Boolean = isFocused,
        pressed: Boolean = isPressed,
        hovered: Boolean = isHovered,
        active: Boolean = isActive,
    ): OnOffButtonState =
        of(
            checked = checked,
            enabled = enabled,
            focused = focused,
            pressed = pressed,
            hovered = hovered,
            active = active,
        )

    override fun toString(): String =
        "${javaClass.simpleName}(isSelected=$isSelected, isEnabled=$isEnabled, isFocused=$isFocused, " +
            "isHovered=$isHovered, isPressed=$isPressed, isActive=$isActive)"

    /** Companion object for [OnOffButtonState]. */
    public companion object {
        /** Constructs an [OnOffButtonState] from individual flags. */
        public fun of(
            checked: Boolean,
            enabled: Boolean = true,
            focused: Boolean = false,
            pressed: Boolean = false,
            hovered: Boolean = false,
            active: Boolean = false,
        ): OnOffButtonState =
            OnOffButtonState(
                (if (checked) Selected else 0UL) or
                    (if (enabled) Enabled else 0UL) or
                    (if (focused) Focused else 0UL) or
                    (if (hovered) Hovered else 0UL) or
                    (if (pressed) Pressed else 0UL) or
                    (if (active) Active else 0UL)
            )
    }
}
