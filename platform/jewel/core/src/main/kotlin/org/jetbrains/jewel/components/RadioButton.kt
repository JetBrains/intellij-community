package org.jetbrains.jewel.components

import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import org.jetbrains.jewel.components.state.ButtonMouseState
import org.jetbrains.jewel.shape
import org.jetbrains.jewel.styles.LocalRadioButtonStyle
import org.jetbrains.jewel.styles.LocalTextStyle
import org.jetbrains.jewel.styles.RadioButtonState
import org.jetbrains.jewel.styles.RadioButtonStyle
import org.jetbrains.jewel.styles.Styles
import org.jetbrains.jewel.styles.withTextStyle

@Composable
fun RadioButton(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    focusable: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    style: RadioButtonStyle = LocalRadioButtonStyle.current,
    variation: Any? = null
) {
    RadioButtonImpl(
        selected,
        onClick,
        modifier,
        enabled,
        focusable,
        interactionSource,
        style,
        variation
    ) { controlModifier, designModifier, _, painter, _, _ ->
        Box(controlModifier.then(designModifier)) {
            if (painter != null) {
                Box(Modifier.paint(painter).fillMaxSize())
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun RadioButtonImpl(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    focusable: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    style: RadioButtonStyle = LocalRadioButtonStyle.current,
    variation: Any? = null,
    content: @Composable (Modifier, Modifier, Int, Painter?, TextStyle, Dp) -> Unit
) {
    var isHovered by remember { mutableStateOf(false) }
    var interactionState by remember(selected, interactionSource, enabled) {
        mutableStateOf(RadioButtonState(selected, ButtonMouseState.None, enabled = enabled))
    }
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> interactionState = interactionState.copy(mouse = ButtonMouseState.Pressed)
                is PressInteraction.Cancel, is PressInteraction.Release -> interactionState = interactionState.copy(
                    mouse = if (isHovered) {
                        ButtonMouseState.Hovered
                    } else {
                        ButtonMouseState.None
                    }
                )
                is FocusInteraction.Focus -> interactionState = interactionState.copy(focused = true)
                is FocusInteraction.Unfocus -> interactionState = interactionState.copy(focused = false)
            }
        }
    }

    val appearance = style.appearance(interactionState, variation)

    val radioButtonPainter = appearance.interiorPainter?.invoke()
    val pointerModifier = if (enabled) {
        Modifier.onPointerEvent(
            PointerEventType.Move
        ) {
        }
            .onPointerEvent(PointerEventType.Enter) {
                isHovered = true
                interactionState = interactionState.copy(mouse = ButtonMouseState.Hovered)
            }
            .onPointerEvent(PointerEventType.Exit) {
                isHovered = false
                interactionState = interactionState.copy(mouse = ButtonMouseState.None)
            }
    } else {
        Modifier
    }

    val clickModifier = Modifier.selectable(
        selected = selected,
        interactionSource = interactionSource,
        indication = null,
        enabled = enabled,
        role = Role.RadioButton,
        onClick = onClick
    )
        .then(pointerModifier)
        .focusable(
            enabled = enabled && focusable,
            interactionSource = interactionSource
        )
        .onKeyEvent {
            val isSpacebarDown = it.key == Key.Spacebar && it.type == KeyEventType.KeyDown
            if (isSpacebarDown) onClick()
            isSpacebarDown
        }

    val haloModifier = if (appearance.haloStroke != null) {
        Modifier.drawBehind {
            val outline = appearance.haloShape.createOutline(size, layoutDirection, this)
            drawOutline(
                outline = outline,
                brush = appearance.haloStroke.brush,
                style = Stroke(appearance.haloStroke.width.toPx())
            )
        }
    } else {
        Modifier
    }

    val designModifier = Modifier.size(appearance.width, appearance.height)
        .shape(appearance.shape, appearance.shapeStroke, appearance.backgroundColor)
        .then(haloModifier)
        .padding(appearance.symbolPadding)

    val baseLine = LocalDensity.current.run { appearance.baseLine.roundToPx() }
    val textStyle = appearance.textStyle
    content(modifier.then(clickModifier), designModifier, baseLine, radioButtonPainter, textStyle, appearance.contentSpacing)
}

@Composable
fun RadioButtonRow(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    focusable: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    style: RadioButtonStyle = LocalRadioButtonStyle.current,
    variation: Any? = null,
    content: @Composable RowScope.() -> Unit
) {
    RadioButtonImpl(
        selected,
        onClick,
        modifier,
        enabled,
        focusable,
        interactionSource,
        style,
        variation
    ) { controlModifier, designModifier, baseLine, painter, textStyle, spacing ->
        Row(
            modifier = controlModifier,
            horizontalArrangement = Arrangement.spacedBy(spacing),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(designModifier.alignBy { baseLine }) {
                if (painter != null) {
                    Box(Modifier.paint(painter).fillMaxSize())
                }
            }
            Styles.withTextStyle(LocalTextStyle.current.merge(textStyle)) {
                content()
            }
        }
    }
}

@Composable
fun RadioButtonRow(
    state: MutableState<Boolean>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    focusable: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    style: RadioButtonStyle = LocalRadioButtonStyle.current,
    variation: Any? = null,
    content: @Composable RowScope.() -> Unit
) = RadioButtonRow(
    state.value,
    { state.value = !state.value },
    modifier, enabled, focusable, interactionSource, style, variation, content
)

@Composable
fun RadioButton(
    state: MutableState<Boolean>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    focusable: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    style: RadioButtonStyle = LocalRadioButtonStyle.current
) = RadioButton(
    state.value,
    { state.value = !state.value },
    modifier,
    enabled,
    focusable,
    interactionSource,
    style
)

@Composable
fun RadioButton(
    text: String,
    selected: Boolean = false,
    onSelectionChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    focusable: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    style: RadioButtonStyle = LocalRadioButtonStyle.current
) = RadioButtonRow(selected, { onSelectionChange(!selected) }, modifier, enabled, focusable, interactionSource, style) {
    Text(text, Modifier.alignByBaseline())
}

@Composable
fun RadioButton(
    text: String,
    state: MutableState<Boolean>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    focusable: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    style: RadioButtonStyle = LocalRadioButtonStyle.current
) = RadioButtonRow(state, modifier, enabled, focusable, interactionSource, style) {
    Text(text, Modifier.alignByBaseline())
}
