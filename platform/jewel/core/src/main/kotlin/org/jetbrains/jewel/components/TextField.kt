package org.jetbrains.jewel.components

import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlinx.coroutines.flow.onEach
import org.jetbrains.jewel.components.state.TextFieldState
import org.jetbrains.jewel.shape
import org.jetbrains.jewel.styles.LocalTextFieldStyle
import org.jetbrains.jewel.styles.LocalTextStyle
import org.jetbrains.jewel.styles.TextFieldStyle

@Composable
fun TextField(
    modifier: Modifier = Modifier,
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    singleLine: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    style: TextFieldStyle = LocalTextFieldStyle.current,
) {
    var inputState by remember(interactionSource, enabled) { mutableStateOf(TextFieldState(enabled)) }

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.onEach { interaction ->
            when (interaction) {
                is FocusInteraction.Focus -> inputState = inputState.copy(focused = true)
                is FocusInteraction.Unfocus -> inputState = inputState.copy(focused = false)
            }
        }
    }

    val appearance = style.appearance(inputState, null)

    val shapeModifier = if (appearance.shapeStroke != null || appearance.backgroundColor != Color.Unspecified)
        Modifier.shape(appearance.shape, appearance.shapeStroke, appearance.backgroundColor)
    else
        Modifier

    val haloStroke = appearance.haloStroke
    val haloModifier = when {
        haloStroke != null -> Modifier.drawBehind {
            drawRect(
                brush = haloStroke.brush,
                style = Stroke(haloStroke.width.toPx()),
            )
        }
        else -> Modifier
    }

    BasicTextField(
        modifier = modifier
            .focusable(enabled, interactionSource)
            .then(shapeModifier)
            .then(haloModifier),
        value = value,
        onValueChange = { onValueChange(it) },
        enabled = enabled,
        readOnly = readOnly,
        singleLine = singleLine,
        textStyle = LocalTextStyle.current.merge(appearance.textStyle),
        cursorBrush = appearance.cursorBrush,
        decorationBox = { coreTextField ->
            Box(Modifier.padding(appearance.contentPadding)) {
                coreTextField()
            }
        }
    )
}
