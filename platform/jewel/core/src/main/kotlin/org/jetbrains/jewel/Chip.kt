package org.jetbrains.jewel

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.isUnspecified
import androidx.compose.ui.semantics.Role
import org.jetbrains.jewel.CommonStateBitMask.Active
import org.jetbrains.jewel.CommonStateBitMask.Enabled
import org.jetbrains.jewel.CommonStateBitMask.Error
import org.jetbrains.jewel.CommonStateBitMask.Focused
import org.jetbrains.jewel.CommonStateBitMask.Hovered
import org.jetbrains.jewel.CommonStateBitMask.Pressed
import org.jetbrains.jewel.CommonStateBitMask.Warning
import org.jetbrains.jewel.foundation.Stroke
import org.jetbrains.jewel.foundation.border
import org.jetbrains.jewel.styling.ChipStyle

@Composable
fun Chip(
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    enabled: Boolean = true,
    style: ChipStyle = IntelliJTheme.chipStyle,
    onChipClick: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    var chipState by remember(interactionSource) {
        mutableStateOf(ChipState.of(enabled = enabled))
    }
    remember(enabled) {
        chipState = chipState.copy(enabled = enabled)
    }

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> chipState = chipState.copy(pressed = true)
                is PressInteraction.Cancel, is PressInteraction.Release -> chipState = chipState.copy(pressed = false)
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

    Row(
        modifier = modifier
            .clickable(
                onClick = onChipClick,
                enabled = enabled,
                role = Role.Button,
                interactionSource = interactionSource,
                indication = null
            )
            .padding(style.metrics.padding)
            .defaultMinSize(style.metrics.minSize.width, style.metrics.minSize.height)
            .background(colors.backgroundFor(chipState).value, shape)
            .border(Stroke.Alignment.Center, style.metrics.borderWidth, borderColor, shape)
            .outline(chipState, shape),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        CompositionLocalProvider(
            LocalContentColor provides (
                colors
                    .contentFor(state = chipState)
                    .value
                    .takeIf { !it.isUnspecified }
                    ?: LocalContentColor.current
                )
        ) {
            content()
        }
    }
}

@Immutable
@JvmInline
value class ChipState(val state: ULong) : StateWithOutline {

    @Stable
    override val isActive: Boolean
        get() = state and Active != 0UL

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
        enabled: Boolean = isEnabled,
        focused: Boolean = isFocused,
        error: Boolean = isError,
        pressed: Boolean = isPressed,
        hovered: Boolean = isHovered,
        warning: Boolean = isWarning,
        active: Boolean = isActive,
    ): ChipState = of(
        enabled = enabled,
        focused = focused,
        error = error,
        pressed = pressed,
        hovered = hovered,
        warning = warning,
        active = active
    )

    override fun toString() =
        "ChipState(isEnabled=$isEnabled, isFocused=$isFocused, isError=$isError, isWarning=$isWarning, " +
            "isHovered=$isHovered, isPressed=$isPressed, isActive=$isActive)"

    companion object {

        fun of(
            enabled: Boolean = true,
            focused: Boolean = false,
            error: Boolean = false,
            pressed: Boolean = false,
            hovered: Boolean = false,
            warning: Boolean = false,
            active: Boolean = false,
        ) = ChipState(
            state = (if (enabled) Enabled else 0UL) or
                (if (focused) Focused else 0UL) or
                (if (error) Error else 0UL) or
                (if (hovered) Hovered else 0UL) or
                (if (pressed) Pressed else 0UL) or
                (if (warning) Warning else 0UL) or
                (if (active) Active else 0UL)
        )
    }
}
