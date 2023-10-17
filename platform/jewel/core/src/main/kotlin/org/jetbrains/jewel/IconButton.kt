package org.jetbrains.jewel

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import org.jetbrains.jewel.styling.IconButtonStyle

@Composable
fun IconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: IconButtonStyle = IntelliJTheme.iconButtonStyle,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable (BoxScope.(ButtonState) -> Unit),
) {
    var buttonState by remember(interactionSource) {
        mutableStateOf(ButtonState.of(enabled = enabled))
    }

    remember(enabled) {
        buttonState = buttonState.copy(enabled = enabled)
    }

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> buttonState = buttonState.copy(pressed = true)
                is PressInteraction.Cancel, is PressInteraction.Release ->
                    buttonState =
                        buttonState.copy(pressed = false)

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
        modifier = modifier
            .defaultMinSize(style.metrics.minSize.width, style.metrics.minSize.height)
            .clickable(
                onClick = onClick,
                enabled = enabled,
                role = Role.Button,
                interactionSource = interactionSource,
                indication = NoIndication,
            )
            .padding(style.metrics.padding)
            .background(background, shape)
            .border(style.metrics.borderWidth, border, shape),
        contentAlignment = Alignment.Center,
        content = {
            onBackground(background) {
                content(buttonState)
            }
        },
    )
}
