package org.jetbrains.jewel.ui.component

import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.zIndex
import org.jetbrains.jewel.foundation.GenerateDataFunctions
import org.jetbrains.jewel.foundation.Stroke
import org.jetbrains.jewel.foundation.modifier.border
import org.jetbrains.jewel.foundation.state.CommonStateBitMask
import org.jetbrains.jewel.foundation.state.FocusableComponentState
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.styling.SegmentedControlButtonStyle
import org.jetbrains.jewel.ui.component.styling.SegmentedControlStyle
import org.jetbrains.jewel.ui.focusOutline
import org.jetbrains.jewel.ui.theme.segmentedControlButtonStyle
import org.jetbrains.jewel.ui.theme.segmentedControlStyle
import org.jetbrains.jewel.ui.util.thenIf

@Composable
public fun SegmentedControl(
    buttons: List<SegmentedControlButtonData>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: SegmentedControlStyle = JewelTheme.segmentedControlStyle,
    buttonStyle: SegmentedControlButtonStyle = JewelTheme.segmentedControlButtonStyle,
    textStyle: TextStyle = JewelTheme.defaultTextStyle,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    var segmentedControlState: SegmentedControlState by remember {
        mutableStateOf(SegmentedControlState.of(enabled = enabled))
    }

    remember(enabled) { segmentedControlState = segmentedControlState.copy(enabled) }

    val borderColor by style.colors.borderFor(segmentedControlState)
    val borderWidth = style.metrics.borderWidth
    val selectedButtonIndex = remember(buttons) { buttons.withIndex().singleOrNull { it.value.selected }?.index ?: -1 }

    Box(modifier) {
        val shape = RoundedCornerShape((style.metrics.cornerSize))
        Box(
            Modifier.matchParentSize()
                .focusOutline(segmentedControlState, shape, expand = -borderWidth)
                .border(Stroke.Alignment.Center, borderWidth, borderColor, shape, expand = -borderWidth)
        )

        Row(
            modifier =
                Modifier.onFocusEvent { segmentedControlState = segmentedControlState.copy(focused = it.isFocused) }
                    .onKeyEvent {
                        when {
                            KeyEventType.KeyUp == it.type && Key.DirectionRight == it.key -> {
                                if (selectedButtonIndex < buttons.size - 1) {
                                    buttons[selectedButtonIndex + 1].onSelect()
                                }
                                true
                            }

                            KeyEventType.KeyUp == it.type && Key.DirectionLeft == it.key -> {
                                if (selectedButtonIndex > 0) {
                                    buttons[selectedButtonIndex - 1].onSelect()
                                }
                                true
                            }

                            else -> false
                        }
                    }
                    .focusable(enabled, interactionSource)
                    .selectableGroup()
                    .padding(style.metrics.borderWidth),
            horizontalArrangement = Arrangement.aligned(Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            buttons.forEach { data ->
                SegmentedControlButton(
                    isActive = segmentedControlState.isActive,
                    isFocused = segmentedControlState.isFocused,
                    enabled = enabled,
                    modifier = Modifier.thenIf(data.selected) { zIndex(1f) },
                    segmentedControlButtonData = data,
                    style = buttonStyle,
                    textStyle = textStyle,
                )
            }
        }
    }
}

@Immutable
@GenerateDataFunctions
public class SegmentedControlButtonData(
    public val selected: Boolean,
    public val content:
        @Composable
        SegmentedControlButtonScope.(segmentedControlButtonState: SegmentedControlButtonState) -> Unit,
    public val onSelect: () -> Unit,
)

@Immutable
@JvmInline
public value class SegmentedControlState(public val state: ULong) : FocusableComponentState {

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
        focused: Boolean = isFocused,
        pressed: Boolean = isPressed,
        hovered: Boolean = isHovered,
        active: Boolean = isActive,
    ): SegmentedControlState =
        of(enabled = enabled, focused = focused, pressed = pressed, hovered = hovered, active = active)

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
        ): SegmentedControlState =
            SegmentedControlState(
                (if (enabled) CommonStateBitMask.Enabled else 0UL) or
                    (if (focused) CommonStateBitMask.Focused else 0UL) or
                    (if (hovered) CommonStateBitMask.Hovered else 0UL) or
                    (if (pressed) CommonStateBitMask.Pressed else 0UL) or
                    (if (active) CommonStateBitMask.Active else 0UL)
            )
    }
}
