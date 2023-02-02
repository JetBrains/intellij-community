@file:OptIn(ExperimentalComposeUiApi::class)

package org.jetbrains.jewel.components

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.semantics.Role
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.jetbrains.jewel.components.state.ButtonMouseState
import org.jetbrains.jewel.components.state.ButtonState
import org.jetbrains.jewel.modifiers.BorderAlignment
import org.jetbrains.jewel.modifiers.background
import org.jetbrains.jewel.modifiers.border
import org.jetbrains.jewel.styles.ButtonAppearance
import org.jetbrains.jewel.styles.ButtonStyle
import org.jetbrains.jewel.styles.LocalButtonStyle
import org.jetbrains.jewel.styles.LocalIconButtonStyle
import org.jetbrains.jewel.styles.LocalTextStyle
import org.jetbrains.jewel.styles.Styles
import org.jetbrains.jewel.styles.withTextStyle

@Composable
fun IconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    style: ButtonStyle = LocalIconButtonStyle.current,
    content: @Composable RowScope.() -> Unit
) = Button(onClick, modifier, enabled, interactionSource, style, content = content)

@Composable
fun ImageButton(
    image: ImageBitmap,
    slices: ImageSliceValues,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    style: ButtonStyle = LocalButtonStyle.current,
    content: @Composable (RowScope.() -> Unit)
) {
    val appearance = style.appearance(ButtonState())
    Box(
        modifier = modifier
            .clickable(
                onClick = onClick,
                enabled = enabled,
                role = Role.Button,
                interactionSource = interactionSource,
                indication = null
            ).background(image, slices),
        propagateMinConstraints = true
    ) {
        ButtonContent(appearance, content)
    }
}

@Composable
fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    style: ButtonStyle = LocalButtonStyle.current,
    variation: Any? = null,
    content: @Composable RowScope.() -> Unit
) {
    var buttonState by remember(interactionSource, enabled) { mutableStateOf(ButtonState(ButtonMouseState.None, enabled)) }
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.onEach { interaction ->
            when (interaction) {
                is PressInteraction.Press -> buttonState = buttonState.copy(mouse = ButtonMouseState.Pressed)

                is PressInteraction.Cancel, is PressInteraction.Release -> buttonState = buttonState.copy(mouse = ButtonMouseState.None)

                is HoverInteraction.Enter -> if (buttonState.mouse == ButtonMouseState.None) {
                    buttonState = buttonState.copy(mouse = ButtonMouseState.Hovered)
                }

                is HoverInteraction.Exit -> if (buttonState.mouse == ButtonMouseState.Hovered) {
                    buttonState = buttonState.copy(mouse = ButtonMouseState.None)
                }

                is FocusInteraction.Focus -> buttonState = buttonState.copy(focused = true)
                is FocusInteraction.Unfocus -> buttonState = buttonState.copy(focused = false)
            }
        }.launchIn(this)
    }

    val appearance = style.appearance(buttonState, variation)

    val backgroundModifier = appearance.background?.let { background ->
        Modifier.background(background, appearance.shape)
    } ?: Modifier

    val borderModifier = appearance.shapeStroke?.let {
        Modifier.border(BorderAlignment.Inside, it.width, it.brush, appearance.shape)
    } ?: Modifier

    val haloModifier = appearance.haloStroke?.let {
        Modifier.border(BorderAlignment.Outside, it.width, it.brush, appearance.shape)
    } ?: Modifier

    Box(
        modifier
            .clickable(
                onClick = onClick,
                enabled = enabled,
                role = Role.Button,
                interactionSource = interactionSource,
                indication = null
            )
            .then(backgroundModifier)
            .then(borderModifier)
            .then(haloModifier),
        propagateMinConstraints = true
    ) {
        ButtonContent(appearance, content)
    }
}

@Composable
private fun ButtonContent(appearance: ButtonAppearance, content: @Composable (RowScope.() -> Unit)) {
    Styles.withTextStyle(LocalTextStyle.current.merge(appearance.textStyle)) {
        Row(
            Modifier
                .padding(appearance.contentPadding)
                .defaultMinSize(appearance.minWidth, appearance.minHeight),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}
