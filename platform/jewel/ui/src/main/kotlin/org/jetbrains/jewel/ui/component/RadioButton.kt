package org.jetbrains.jewel.ui.component

import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import org.jetbrains.jewel.foundation.Stroke
import org.jetbrains.jewel.foundation.state.CommonStateBitMask.Active
import org.jetbrains.jewel.foundation.state.CommonStateBitMask.Enabled
import org.jetbrains.jewel.foundation.state.CommonStateBitMask.Focused
import org.jetbrains.jewel.foundation.state.CommonStateBitMask.Hovered
import org.jetbrains.jewel.foundation.state.CommonStateBitMask.Pressed
import org.jetbrains.jewel.foundation.state.CommonStateBitMask.Selected
import org.jetbrains.jewel.foundation.state.FocusableComponentState
import org.jetbrains.jewel.foundation.state.SelectableComponentState
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.foundation.theme.LocalContentColor
import org.jetbrains.jewel.foundation.theme.LocalTextStyle
import org.jetbrains.jewel.ui.Outline
import org.jetbrains.jewel.ui.component.styling.RadioButtonStyle
import org.jetbrains.jewel.ui.outline
import org.jetbrains.jewel.ui.painter.hints.Selected
import org.jetbrains.jewel.ui.painter.hints.Stateful
import org.jetbrains.jewel.ui.painter.rememberResourcePainterProvider
import org.jetbrains.jewel.ui.theme.radioButtonStyle

@Composable
public fun RadioButton(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    outline: Outline = Outline.None,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    style: RadioButtonStyle = JewelTheme.radioButtonStyle,
    textStyle: TextStyle = JewelTheme.defaultTextStyle,
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
) {
    RadioButtonImpl(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        outline = outline,
        interactionSource = interactionSource,
        style = style,
        textStyle = textStyle,
        verticalAlignment = verticalAlignment,
        content = null,
    )
}

@Composable
public fun RadioButtonRow(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    outline: Outline = Outline.None,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    style: RadioButtonStyle = JewelTheme.radioButtonStyle,
    textStyle: TextStyle = JewelTheme.defaultTextStyle,
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
) {
    RadioButtonImpl(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        outline = outline,
        interactionSource = interactionSource,
        style = style,
        textStyle = textStyle,
        verticalAlignment = verticalAlignment,
    ) {
        Text(text)
    }
}

@Composable
public fun RadioButtonRow(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    outline: Outline = Outline.None,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    style: RadioButtonStyle = JewelTheme.radioButtonStyle,
    textStyle: TextStyle = JewelTheme.defaultTextStyle,
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    content: @Composable RowScope.() -> Unit,
) {
    RadioButtonImpl(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        outline = outline,
        interactionSource = interactionSource,
        style = style,
        textStyle = textStyle,
        verticalAlignment = verticalAlignment,
        content = content,
    )
}

@Composable
private fun RadioButtonImpl(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    outline: Outline,
    interactionSource: MutableInteractionSource,
    style: RadioButtonStyle,
    textStyle: TextStyle,
    verticalAlignment: Alignment.Vertical,
    content: (@Composable RowScope.() -> Unit)?,
) {
    var radioButtonState by remember { mutableStateOf(RadioButtonState.of(selected = selected, enabled = enabled)) }

    remember(selected, enabled) { radioButtonState = radioButtonState.copy(selected = selected, enabled = enabled) }

    val swingCompatMode = JewelTheme.isSwingCompatMode
    LaunchedEffect(interactionSource, swingCompatMode) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> radioButtonState = radioButtonState.copy(pressed = !swingCompatMode)

                is PressInteraction.Cancel,
                is PressInteraction.Release -> radioButtonState = radioButtonState.copy(pressed = false)

                is HoverInteraction.Enter -> radioButtonState = radioButtonState.copy(hovered = !swingCompatMode)

                is HoverInteraction.Exit -> radioButtonState = radioButtonState.copy(hovered = false)
                is FocusInteraction.Focus -> radioButtonState = radioButtonState.copy(focused = true)
                is FocusInteraction.Unfocus -> radioButtonState = radioButtonState.copy(focused = false)
            }
        }
    }

    val wrapperModifier =
        modifier.selectable(
            selected = selected,
            onClick = onClick,
            enabled = enabled,
            role = Role.RadioButton,
            interactionSource = interactionSource,
            indication = null,
        )

    val colors = style.colors
    val metrics = style.metrics
    val outlineModifier =
        Modifier.size(metrics.outlineSizeFor(radioButtonState).value)
            .outline(
                state = radioButtonState,
                outline = outline,
                outlineShape = CircleShape,
                alignment = Stroke.Alignment.Center,
            )

    val radioButtonPainterProvider = rememberResourcePainterProvider(style.icons.radioButton)
    val radioButtonPainter by
        radioButtonPainterProvider.getPainter(Selected(radioButtonState), Stateful(radioButtonState))

    val radioButtonBoxModifier = Modifier.size(metrics.radioButtonSize)

    if (content == null) {
        Box(radioButtonBoxModifier, contentAlignment = Alignment.Center) {
            RadioButtonImage(radioButtonPainter)
            Box(outlineModifier)
        }
    } else {
        Row(
            wrapperModifier,
            horizontalArrangement = Arrangement.spacedBy(metrics.iconContentGap),
            verticalAlignment = verticalAlignment,
        ) {
            Box(radioButtonBoxModifier, contentAlignment = Alignment.Center) {
                RadioButtonImage(radioButtonPainter)
                Box(outlineModifier)
            }

            val contentColor by colors.contentFor(radioButtonState)
            val resolvedContentColor =
                contentColor.takeOrElse { textStyle.color }.takeOrElse { LocalContentColor.current }

            CompositionLocalProvider(
                LocalTextStyle provides textStyle.copy(color = contentColor.takeOrElse { textStyle.color }),
                LocalContentColor provides resolvedContentColor,
            ) {
                content()
            }
        }
    }
}

@Composable
private fun RadioButtonImage(radioButtonPainter: Painter, modifier: Modifier = Modifier) {
    Box(modifier.paint(radioButtonPainter, alignment = Alignment.TopStart))
}

@Immutable
@JvmInline
public value class RadioButtonState(public val state: ULong) : SelectableComponentState, FocusableComponentState {
    override val isActive: Boolean
        get() = state and Active != 0UL

    override val isSelected: Boolean
        get() = state and Selected != 0UL

    override val isEnabled: Boolean
        get() = state and Enabled != 0UL

    override val isFocused: Boolean
        get() = state and Focused != 0UL

    override val isHovered: Boolean
        get() = state and Hovered != 0UL

    override val isPressed: Boolean
        get() = state and Pressed != 0UL

    public fun copy(
        selected: Boolean = isSelected,
        enabled: Boolean = isEnabled,
        focused: Boolean = isFocused,
        pressed: Boolean = isPressed,
        hovered: Boolean = isHovered,
        active: Boolean = isActive,
    ): RadioButtonState =
        of(
            selected = selected,
            enabled = enabled,
            focused = focused,
            pressed = pressed,
            hovered = hovered,
            active = active,
        )

    override fun toString(): String =
        "${javaClass.simpleName}(isSelected=$isSelected, isEnabled=$isEnabled, isFocused=$isFocused, " +
            "isHovered=$isHovered, isPressed=$isPressed, isActive=$isActive)"

    public companion object {
        public fun of(
            selected: Boolean,
            enabled: Boolean = true,
            focused: Boolean = false,
            pressed: Boolean = false,
            hovered: Boolean = false,
            active: Boolean = false,
        ): RadioButtonState =
            RadioButtonState(
                (if (selected) Selected else 0UL) or
                    (if (enabled) Enabled else 0UL) or
                    (if (focused) Focused else 0UL) or
                    (if (pressed) Pressed else 0UL) or
                    (if (hovered) Hovered else 0UL) or
                    (if (active) Active else 0UL)
            )
    }
}
