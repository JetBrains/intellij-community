package org.jetbrains.jewel.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.TextStyle
import org.jetbrains.jewel.foundation.Stroke
import org.jetbrains.jewel.foundation.modifier.border
import org.jetbrains.jewel.foundation.state.CommonStateBitMask.Active
import org.jetbrains.jewel.foundation.state.CommonStateBitMask.Enabled
import org.jetbrains.jewel.foundation.state.CommonStateBitMask.Focused
import org.jetbrains.jewel.foundation.state.CommonStateBitMask.Hovered
import org.jetbrains.jewel.foundation.state.CommonStateBitMask.Pressed
import org.jetbrains.jewel.foundation.state.FocusableComponentState
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.foundation.theme.LocalContentColor
import org.jetbrains.jewel.foundation.theme.LocalTextStyle
import org.jetbrains.jewel.ui.component.styling.ButtonStyle
import org.jetbrains.jewel.ui.focusOutline
import org.jetbrains.jewel.ui.theme.defaultButtonStyle
import org.jetbrains.jewel.ui.theme.outlinedButtonStyle

@Composable
public fun DefaultButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    style: ButtonStyle = JewelTheme.defaultButtonStyle,
    textStyle: TextStyle = JewelTheme.textStyle,
    content: @Composable RowScope.() -> Unit,
) {
    ButtonImpl(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        interactionSource = interactionSource,
        style = style,
        content = content,
        textStyle = textStyle,
    )
}

@Composable
public fun OutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    style: ButtonStyle = JewelTheme.outlinedButtonStyle,
    textStyle: TextStyle = JewelTheme.textStyle,
    content: @Composable RowScope.() -> Unit,
) {
    ButtonImpl(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        interactionSource = interactionSource,
        style = style,
        content = content,
        textStyle = textStyle,
    )
}

@Composable
private fun ButtonImpl(
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    interactionSource: MutableInteractionSource,
    style: ButtonStyle,
    textStyle: TextStyle,
    content: @Composable RowScope.() -> Unit,
) {
    var buttonState by remember(interactionSource) {
        mutableStateOf(ButtonState.of(enabled = enabled))
    }

    remember(enabled) { buttonState = buttonState.copy(enabled = enabled) }

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> buttonState = buttonState.copy(pressed = true)
                is PressInteraction.Cancel, is PressInteraction.Release ->
                    buttonState = buttonState.copy(pressed = false)

                is HoverInteraction.Enter -> buttonState = buttonState.copy(hovered = true)
                is HoverInteraction.Exit -> buttonState = buttonState.copy(hovered = false)
                is FocusInteraction.Focus -> buttonState = buttonState.copy(focused = true)
                is FocusInteraction.Unfocus -> buttonState = buttonState.copy(focused = false)
            }
        }
    }

    val shape = RoundedCornerShape(style.metrics.cornerSize)
    val colors = style.colors
    val borderColor by colors.borderFor(buttonState)

    Box(
        modifier = modifier
            .clickable(
                onClick = onClick,
                enabled = enabled,
                role = Role.Button,
                interactionSource = interactionSource,
                indication = null,
            )
            .background(colors.backgroundFor(buttonState).value, shape)
            .border(Stroke.Alignment.Center, style.metrics.borderWidth, borderColor, shape)
            .focusOutline(buttonState, shape),
        propagateMinConstraints = true,
    ) {
        val contentColor by colors.contentFor(buttonState)

        CompositionLocalProvider(
            LocalContentColor provides contentColor.takeOrElse { textStyle.color },
            LocalTextStyle provides textStyle.copy(color = contentColor.takeOrElse { textStyle.color }),
        ) {
            Row(
                Modifier.defaultMinSize(style.metrics.minSize.width, style.metrics.minSize.height)
                    .padding(style.metrics.padding),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )
        }
    }
}

@Immutable
@JvmInline
public value class ButtonState(public val state: ULong) : FocusableComponentState {

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
    ): ButtonState =
        of(
            enabled = enabled,
            focused = focused,
            pressed = pressed,
            hovered = hovered,
            active = active,
        )

    override fun toString(): String =
        "${javaClass.simpleName}(isEnabled=$isEnabled, isFocused=$isFocused, isHovered=$isHovered, " +
            "isPressed=$isPressed, isActive=$isActive)"

    public companion object {

        public fun of(
            enabled: Boolean = true,
            focused: Boolean = false,
            pressed: Boolean = false,
            hovered: Boolean = false,
            active: Boolean = true,
        ): ButtonState =
            ButtonState(
                (if (enabled) Enabled else 0UL) or
                    (if (focused) Focused else 0UL) or
                    (if (hovered) Hovered else 0UL) or
                    (if (pressed) Pressed else 0UL) or
                    (if (active) Active else 0UL),
            )
    }
}
