package org.jetbrains.jewel.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import org.jetbrains.jewel.foundation.Stroke
import org.jetbrains.jewel.foundation.modifier.border
import org.jetbrains.jewel.foundation.state.CommonStateBitMask.Active
import org.jetbrains.jewel.foundation.state.CommonStateBitMask.Enabled
import org.jetbrains.jewel.foundation.state.CommonStateBitMask.Hovered
import org.jetbrains.jewel.foundation.state.CommonStateBitMask.Pressed
import org.jetbrains.jewel.foundation.state.CommonStateBitMask.Selected
import org.jetbrains.jewel.foundation.state.SelectableComponentState
import org.jetbrains.jewel.foundation.theme.LocalContentColor
import org.jetbrains.jewel.foundation.theme.LocalTextStyle
import org.jetbrains.jewel.ui.component.styling.SegmentedControlButtonStyle

public interface SegmentedControlButtonScope

internal class SegmentedControlButtonScopeContainer : SegmentedControlButtonScope

@Composable
internal fun SegmentedControlButton(
    isActive: Boolean,
    isFocused: Boolean,
    enabled: Boolean,
    segmentedControlButtonData: SegmentedControlButtonData,
    style: SegmentedControlButtonStyle,
    textStyle: TextStyle,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    var buttonState by remember {
        mutableStateOf(
            SegmentedControlButtonState.of(
                selected = segmentedControlButtonData.selected,
                active = isActive,
                enabled = enabled,
            )
        )
    }
    remember(segmentedControlButtonData.selected, enabled, isActive) {
        buttonState =
            buttonState.copy(selected = segmentedControlButtonData.selected, active = isActive, enabled = enabled)
    }

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> buttonState = buttonState.copy(pressed = true)
                is PressInteraction.Cancel,
                is PressInteraction.Release -> buttonState = buttonState.copy(pressed = false)

                is HoverInteraction.Enter -> buttonState = buttonState.copy(hovered = true)
                is HoverInteraction.Exit -> buttonState = buttonState.copy(hovered = false)
            }
        }
    }

    val shape = RoundedCornerShape(style.metrics.cornerSize)
    val colors = style.colors
    val backgroundColor by colors.backgroundFor(buttonState, isFocused)
    val borderColor by colors.borderFor(buttonState, isFocused)

    Box(
        modifier =
            modifier
                .focusProperties { canFocus = false }
                .selectable(
                    selected = buttonState.isSelected,
                    interactionSource = interactionSource,
                    enabled = enabled,
                    indication = null,
                    role = Role.Button,
                    onClick = segmentedControlButtonData.onSelect,
                )
                .background(backgroundColor, shape)
                .border(alignment = Stroke.Alignment.Center, width = style.metrics.borderWidth, borderColor, shape),
        propagateMinConstraints = true,
    ) {
        val contentColor by colors.contentFor(buttonState)

        CompositionLocalProvider(
            LocalContentColor provides contentColor.takeOrElse { LocalContentColor.current },
            LocalTextStyle provides textStyle.copy(color = contentColor.takeOrElse { textStyle.color }),
        ) {
            Row(
                Modifier.defaultMinSize(style.metrics.minSize.width, style.metrics.minSize.height)
                    .padding(style.metrics.segmentedButtonPadding),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                segmentedControlButtonData.content(SegmentedControlButtonScopeContainer(), buttonState)
            }
        }
    }
}

@Immutable
@JvmInline
public value class SegmentedControlButtonState(public val state: ULong) : SelectableComponentState {

    override val isActive: Boolean
        get() = state and Active != 0UL

    override val isSelected: Boolean
        get() = state and Selected != 0UL

    override val isEnabled: Boolean
        get() = state and Enabled != 0UL

    override val isHovered: Boolean
        get() = state and Hovered != 0UL

    override val isPressed: Boolean
        get() = state and Pressed != 0UL

    public fun copy(
        selected: Boolean = isSelected,
        enabled: Boolean = isEnabled,
        pressed: Boolean = isPressed,
        hovered: Boolean = isHovered,
        active: Boolean = isActive,
    ): SegmentedControlButtonState =
        of(selected = selected, enabled = enabled, pressed = pressed, hovered = hovered, active = active)

    override fun toString(): String =
        "${javaClass.simpleName}(isSelected=$isSelected, isEnabled=$isEnabled, " +
            "isHovered=$isHovered, isPressed=$isPressed, isActive=$isActive)"

    public companion object {

        public fun of(
            selected: Boolean,
            enabled: Boolean = true,
            pressed: Boolean = false,
            hovered: Boolean = false,
            active: Boolean = false,
        ): SegmentedControlButtonState =
            SegmentedControlButtonState(
                (if (selected) Selected else 0UL) or
                    (if (enabled) Enabled else 0UL) or
                    (if (pressed) Pressed else 0UL) or
                    (if (hovered) Hovered else 0UL) or
                    (if (active) Active else 0UL)
            )
    }
}
