package org.jetbrains.jewel.components

import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.triStateToggleable
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import org.jetbrains.jewel.components.state.ButtonMouseState
import org.jetbrains.jewel.components.state.CheckboxState
import org.jetbrains.jewel.shape
import org.jetbrains.jewel.styles.CheckboxStyle
import org.jetbrains.jewel.styles.LocalCheckboxStyle
import org.jetbrains.jewel.styles.LocalTextStyle
import org.jetbrains.jewel.styles.Styles
import org.jetbrains.jewel.styles.withTextStyle

@Composable
fun Checkbox(
    state: ToggleableState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    style: CheckboxStyle = LocalCheckboxStyle.current,
    variation: Any? = null
) {
    CheckboxImpl(
        state,
        onClick,
        modifier,
        enabled,
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
fun CheckboxImpl(
    state: ToggleableState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    style: CheckboxStyle = LocalCheckboxStyle.current,
    variation: Any? = null,
    content: @Composable (Modifier, Modifier, Int, Painter?, TextStyle, Dp) -> Unit
) {
    var interactionState by remember(interactionSource, enabled) {
        mutableStateOf(CheckboxState(state, ButtonMouseState.None, enabled = enabled))
    }
    remember(state) {
        interactionState = interactionState.copy(toggle = state)
    }
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> interactionState = interactionState.copy(mouse = ButtonMouseState.Pressed)

                is PressInteraction.Cancel, is PressInteraction.Release -> interactionState = interactionState.copy(mouse = ButtonMouseState.None)

                is HoverInteraction.Enter -> if (interactionState.mouse == ButtonMouseState.None) {
                    interactionState = interactionState.copy(mouse = ButtonMouseState.Hovered)
                }

                is HoverInteraction.Exit -> if (interactionState.mouse == ButtonMouseState.Hovered) {
                    interactionState = interactionState.copy(mouse = ButtonMouseState.None)
                }

                is FocusInteraction.Focus -> interactionState = interactionState.copy(focused = true)
                is FocusInteraction.Unfocus -> interactionState = interactionState.copy(focused = false)
            }
        }
    }

    val appearance = style.appearance(interactionState, variation)

    val checkboxPainter = appearance.interiorPainter?.invoke()

    val clickModifier = Modifier.triStateToggleable(
        state = state,
        onClick = { onClick() },
        enabled = enabled,
        role = Role.Checkbox,
        interactionSource = interactionSource,
        indication = null
    )

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
    content(modifier.then(clickModifier), designModifier, baseLine, checkboxPainter, textStyle, appearance.contentSpacing)
}

@Composable
fun CheckboxRow(
    state: ToggleableState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    style: CheckboxStyle = LocalCheckboxStyle.current,
    variation: Any? = null,
    content: @Composable RowScope.() -> Unit
) {
    CheckboxImpl(
        state,
        onClick,
        modifier,
        enabled,
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
fun CheckboxRow(
    state: MutableState<Boolean>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    style: CheckboxStyle = LocalCheckboxStyle.current,
    variation: Any? = null,
    content: @Composable RowScope.() -> Unit
) = CheckboxRow(
    ToggleableState(state.value),
    { state.value = !state.value },
    modifier,
    enabled,
    interactionSource,
    style,
    variation,
    content
)

@Composable
fun Checkbox(
    checked: Boolean = false,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    style: CheckboxStyle = LocalCheckboxStyle.current
) = Checkbox(
    ToggleableState(checked),
    { onCheckedChange(!checked) },
    modifier,
    enabled,
    interactionSource,
    style
)

@Composable
fun Checkbox(
    state: MutableState<Boolean>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    style: CheckboxStyle = LocalCheckboxStyle.current
) = Checkbox(
    ToggleableState(state.value),
    { state.value = !state.value },
    modifier,
    enabled,
    interactionSource,
    style
)

@Composable
fun Checkbox(
    text: String,
    checked: Boolean = false,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    style: CheckboxStyle = LocalCheckboxStyle.current
) = CheckboxRow(checked, onCheckedChange, modifier, enabled, interactionSource, style) {
    Text(text, Modifier.alignByBaseline())
}

@Composable
fun Checkbox(
    text: String,
    state: MutableState<Boolean>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    style: CheckboxStyle = LocalCheckboxStyle.current
) = CheckboxRow(state, modifier, enabled, interactionSource, style) {
    Text(text, Modifier.alignByBaseline())
}

@Composable
fun CheckboxRow(
    checked: Boolean = false,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    style: CheckboxStyle = LocalCheckboxStyle.current,
    variation: Any? = null,
    content: @Composable RowScope.() -> Unit
) = CheckboxRow(
    ToggleableState(checked),
    { onCheckedChange(!checked) },
    modifier,
    enabled,
    interactionSource,
    style,
    variation,
    content
)
