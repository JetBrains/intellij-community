@file:OptIn(ExperimentalComposeUiApi::class)

package org.jetbrains.jewel.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.FocusInteraction
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.semantics.Role
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.jetbrains.jewel.components.state.ButtonMouseState
import org.jetbrains.jewel.components.state.ButtonState
import org.jetbrains.jewel.modifiers.background
import org.jetbrains.jewel.shape
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
    focusable: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    style: ButtonStyle = LocalIconButtonStyle.current,
    content: @Composable RowScope.() -> Unit
) = Button(onClick, modifier, enabled, focusable, interactionSource, style, content = content)

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
    focusable: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    style: ButtonStyle = LocalButtonStyle.current,
    variation: Any? = null,
    content: @Composable RowScope.() -> Unit
) {
    var isHovered by remember { mutableStateOf(false) }
    var buttonState by remember(interactionSource, enabled) { mutableStateOf(ButtonState(ButtonMouseState.None, enabled)) }
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.onEach { interaction ->
            when (interaction) {
                is PressInteraction.Press -> buttonState = buttonState.copy(mouse = ButtonMouseState.Pressed)
                is PressInteraction.Cancel, is PressInteraction.Release -> buttonState = buttonState.copy(
                    mouse = if (isHovered) {
                        ButtonMouseState.Hovered
                    } else {
                        ButtonMouseState.None
                    }
                )

                is FocusInteraction.Focus -> buttonState = buttonState.copy(focused = true)
                is FocusInteraction.Unfocus -> buttonState = buttonState.copy(focused = false)
            }
        }.launchIn(this)
    }

    val appearance = style.appearance(buttonState, variation)

    val shapeModifier = if (appearance.shapeStroke != null || appearance.background != null) {
        Modifier.shape(appearance.shape, appearance.shapeStroke, appearance.background)
    } else {
        Modifier
    }

    val haloStroke = appearance.haloStroke
    val haloModifier = if (haloStroke != null) {
        Modifier.drawBehind {
            val outline = appearance.haloShape.createOutline(size, layoutDirection, this)
            drawOutline(
                outline = outline,
                brush = haloStroke.brush,
                style = Stroke(haloStroke.width.toPx())
            )
        }
    } else {
        Modifier
    }

    val pointerModifier = if (enabled) {
        Modifier
            .onPointerEvent(PointerEventType.Enter) {
                isHovered = true
                buttonState = buttonState.copy(mouse = ButtonMouseState.Hovered)
            }
            .onPointerEvent(PointerEventType.Exit) {
                isHovered = false
                buttonState = buttonState.copy(mouse = ButtonMouseState.None)
            }
    } else {
        Modifier
    }

    Box(
        modifier
            .clickable(
                onClick = onClick,
                enabled = enabled,
                role = Role.Button,
                interactionSource = interactionSource,
                indication = null
            )
            .focusable(
                enabled = enabled && focusable,
                interactionSource = interactionSource
            )
            .then(pointerModifier)
            .then(shapeModifier)
            .then(haloModifier)
            .clip(appearance.shape),
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
