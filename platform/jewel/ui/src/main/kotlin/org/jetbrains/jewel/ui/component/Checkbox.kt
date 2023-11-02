package org.jetbrains.jewel.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.triStateToggleable
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
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.TextStyle
import org.jetbrains.jewel.foundation.Stroke
import org.jetbrains.jewel.foundation.state.CommonStateBitMask.Active
import org.jetbrains.jewel.foundation.state.CommonStateBitMask.Enabled
import org.jetbrains.jewel.foundation.state.CommonStateBitMask.Focused
import org.jetbrains.jewel.foundation.state.CommonStateBitMask.Hovered
import org.jetbrains.jewel.foundation.state.CommonStateBitMask.Indeterminate
import org.jetbrains.jewel.foundation.state.CommonStateBitMask.Pressed
import org.jetbrains.jewel.foundation.state.CommonStateBitMask.Selected
import org.jetbrains.jewel.foundation.state.FocusableComponentState
import org.jetbrains.jewel.foundation.state.ToggleableComponentState
import org.jetbrains.jewel.foundation.state.ToggleableComponentState.Companion.readToggleableState
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.foundation.theme.LocalContentColor
import org.jetbrains.jewel.foundation.theme.LocalTextStyle
import org.jetbrains.jewel.ui.Outline
import org.jetbrains.jewel.ui.component.styling.CheckboxColors
import org.jetbrains.jewel.ui.component.styling.CheckboxIcons
import org.jetbrains.jewel.ui.component.styling.CheckboxMetrics
import org.jetbrains.jewel.ui.component.styling.LocalCheckboxStyle
import org.jetbrains.jewel.ui.outline
import org.jetbrains.jewel.ui.painter.PainterHint
import org.jetbrains.jewel.ui.painter.PainterProviderScope
import org.jetbrains.jewel.ui.painter.PainterSuffixHint
import org.jetbrains.jewel.ui.painter.hints.Selected
import org.jetbrains.jewel.ui.painter.hints.Stateful
import org.jetbrains.jewel.ui.theme.checkboxStyle

@Composable
public fun Checkbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    outline: Outline = Outline.None,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    colors: CheckboxColors = JewelTheme.checkboxStyle.colors,
    metrics: CheckboxMetrics = JewelTheme.checkboxStyle.metrics,
    icons: CheckboxIcons = JewelTheme.checkboxStyle.icons,
    textStyle: TextStyle = LocalTextStyle.current,
) {
    val state by remember(checked) { mutableStateOf(ToggleableState(checked)) }
    CheckboxImpl(
        state = state,
        onClick = { onCheckedChange.invoke(!checked) },
        modifier = modifier,
        enabled = enabled,
        outline = outline,
        interactionSource = interactionSource,
        colors = colors,
        metrics = metrics,
        icons = icons,
        textStyle = textStyle,
        content = null,
    )
}

@Composable
public fun TriStateCheckbox(
    state: ToggleableState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    outline: Outline = Outline.None,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    colors: CheckboxColors = LocalCheckboxStyle.current.colors,
    metrics: CheckboxMetrics = LocalCheckboxStyle.current.metrics,
    icons: CheckboxIcons = LocalCheckboxStyle.current.icons,
    textStyle: TextStyle = LocalTextStyle.current,
) {
    CheckboxImpl(
        state = state,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        outline = outline,
        interactionSource = interactionSource,
        colors = colors,
        metrics = metrics,
        icons = icons,
        textStyle = textStyle,
        content = null,
    )
}

@Composable
public fun TriStateCheckboxRow(
    text: String,
    state: ToggleableState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    outline: Outline = Outline.None,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    colors: CheckboxColors = LocalCheckboxStyle.current.colors,
    metrics: CheckboxMetrics = LocalCheckboxStyle.current.metrics,
    icons: CheckboxIcons = LocalCheckboxStyle.current.icons,
    textStyle: TextStyle = LocalTextStyle.current,
) {
    CheckboxImpl(
        state = state,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        outline = outline,
        interactionSource = interactionSource,
        colors = colors,
        metrics = metrics,
        icons = icons,
        textStyle = textStyle,
    ) {
        Text(text)
    }
}

@Composable
public fun CheckboxRow(
    text: String,
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    outline: Outline = Outline.None,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    colors: CheckboxColors = LocalCheckboxStyle.current.colors,
    metrics: CheckboxMetrics = LocalCheckboxStyle.current.metrics,
    icons: CheckboxIcons = LocalCheckboxStyle.current.icons,
    textStyle: TextStyle = LocalTextStyle.current,
) {
    val state by remember(checked) { mutableStateOf(ToggleableState(checked)) }

    CheckboxImpl(
        state = state,
        onClick = { onCheckedChange?.invoke(!checked) },
        modifier = modifier,
        enabled = enabled,
        outline = outline,
        interactionSource = interactionSource,
        colors = colors,
        metrics = metrics,
        icons = icons,
        textStyle = textStyle,
    ) {
        Text(text)
    }
}

@Composable
public fun CheckboxRow(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    outline: Outline = Outline.None,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    colors: CheckboxColors = LocalCheckboxStyle.current.colors,
    metrics: CheckboxMetrics = LocalCheckboxStyle.current.metrics,
    icons: CheckboxIcons = LocalCheckboxStyle.current.icons,
    textStyle: TextStyle = LocalTextStyle.current,
    content: @Composable RowScope.() -> Unit,
) {
    CheckboxImpl(
        state = ToggleableState(checked),
        onClick = { onCheckedChange?.invoke(!checked) },
        modifier = modifier,
        enabled = enabled,
        outline = outline,
        interactionSource = interactionSource,
        colors = colors,
        metrics = metrics,
        icons = icons,
        textStyle = textStyle,
        content = content,
    )
}

@Composable
public fun TriStateCheckboxRow(
    state: ToggleableState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    outline: Outline = Outline.None,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    colors: CheckboxColors = LocalCheckboxStyle.current.colors,
    metrics: CheckboxMetrics = LocalCheckboxStyle.current.metrics,
    icons: CheckboxIcons = LocalCheckboxStyle.current.icons,
    textStyle: TextStyle = LocalTextStyle.current,
    content: @Composable RowScope.() -> Unit,
) {
    CheckboxImpl(
        state = state,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        outline = outline,
        interactionSource = interactionSource,
        colors = colors,
        metrics = metrics,
        icons = icons,
        textStyle = textStyle,
        content = content,
    )
}

@Composable
private fun CheckboxImpl(
    state: ToggleableState,
    onClick: () -> Unit,
    colors: CheckboxColors,
    metrics: CheckboxMetrics,
    icons: CheckboxIcons,
    modifier: Modifier,
    enabled: Boolean,
    outline: Outline,
    interactionSource: MutableInteractionSource,
    textStyle: TextStyle,
    content: (@Composable RowScope.() -> Unit)?,
) {
    var checkboxState by remember(interactionSource) {
        mutableStateOf(CheckboxState.of(state, enabled = enabled))
    }

    remember(state, enabled) {
        checkboxState = checkboxState.copy(toggleableState = state, enabled = enabled)
    }

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> checkboxState = checkboxState.copy(pressed = true)
                is PressInteraction.Cancel,
                is PressInteraction.Release,
                -> checkboxState = checkboxState.copy(pressed = false)
                is HoverInteraction.Enter -> checkboxState = checkboxState.copy(hovered = true)
                is HoverInteraction.Exit -> checkboxState = checkboxState.copy(hovered = false)
                is FocusInteraction.Focus -> checkboxState = checkboxState.copy(focused = true)
                is FocusInteraction.Unfocus -> checkboxState = checkboxState.copy(focused = false)
            }
        }
    }

    if (JewelTheme.isSwingCompatMode) {
        checkboxState = checkboxState.copy(hovered = false, pressed = false)
    }

    val wrapperModifier =
        modifier.triStateToggleable(
            state = state,
            onClick = onClick,
            enabled = enabled,
            role = Role.Checkbox,
            interactionSource = interactionSource,
            indication = null,
        )

    val checkBoxImageModifier = Modifier.size(metrics.checkboxSize)
    val outlineModifier = Modifier.size(metrics.outlineSize)
        .offset(metrics.outlineOffset.x, metrics.outlineOffset.y)
        .outline(
            state = checkboxState,
            outline = outline,
            outlineShape = RoundedCornerShape(metrics.checkboxCornerSize),
            alignment = Stroke.Alignment.Center,
        )

    val checkboxPainter by icons.checkbox.getPainter(
        if (checkboxState.toggleableState == ToggleableState.Indeterminate) {
            CheckBoxIndeterminate
        } else {
            PainterHint.None
        },
        Selected(checkboxState),
        Stateful(checkboxState),
    )

    if (content == null) {
        Box(contentAlignment = Alignment.TopStart) {
            CheckBoxImage(wrapperModifier, checkboxPainter, checkBoxImageModifier)
            Box(outlineModifier)
        }
    } else {
        Row(
            wrapperModifier,
            horizontalArrangement = Arrangement.spacedBy(metrics.iconContentGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(contentAlignment = Alignment.TopStart) {
                CheckBoxImage(Modifier, checkboxPainter, checkBoxImageModifier)
                Box(outlineModifier)
            }

            val contentColor by colors.contentFor(checkboxState)
            CompositionLocalProvider(
                LocalTextStyle provides textStyle.copy(color = contentColor.takeOrElse { textStyle.color }),
                LocalContentColor provides contentColor.takeOrElse { LocalContentColor.current },
            ) {
                content()
            }
        }
    }
}

private object CheckBoxIndeterminate : PainterSuffixHint() {

    override fun PainterProviderScope.suffix(): String = "Indeterminate"
}

@Composable
private fun CheckBoxImage(
    modifier: Modifier,
    checkboxPainter: Painter,
    checkBoxModifier: Modifier,
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Image(checkboxPainter, contentDescription = null, modifier = checkBoxModifier)
    }
}

@Immutable
@JvmInline
public value class CheckboxState(private val state: ULong) : ToggleableComponentState, FocusableComponentState {

    override val toggleableState: ToggleableState
        get() = state.readToggleableState()

    override val isEnabled: Boolean
        get() = state and Enabled != 0UL

    override val isActive: Boolean
        get() = state and Active != 0UL

    override val isSelected: Boolean
        get() = toggleableState != ToggleableState.Off

    override val isFocused: Boolean
        get() = state and Focused != 0UL

    override val isHovered: Boolean
        get() = state and Hovered != 0UL

    override val isPressed: Boolean
        get() = state and Pressed != 0UL

    public fun copy(
        toggleableState: ToggleableState = this.toggleableState,
        enabled: Boolean = isEnabled,
        focused: Boolean = isFocused,
        pressed: Boolean = isPressed,
        hovered: Boolean = isHovered,
        active: Boolean = isActive,
    ): CheckboxState =
        of(
            toggleableState = toggleableState,
            enabled = enabled,
            focused = focused,
            pressed = pressed,
            hovered = hovered,
            active = active,
        )

    override fun toString(): String =
        "${javaClass.simpleName}(toggleableState=$toggleableState, isEnabled=$isEnabled, isFocused=$isFocused, " +
            "isHovered=$isHovered, isPressed=$isPressed, isSelected=$isSelected, isActive=$isActive)"

    public companion object {

        public fun of(
            toggleableState: ToggleableState,
            enabled: Boolean = true,
            focused: Boolean = false,
            pressed: Boolean = false,
            hovered: Boolean = false,
            active: Boolean = false,
        ): CheckboxState =
            CheckboxState(
                (if (enabled) Enabled else 0UL) or
                    (if (focused) Focused else 0UL) or
                    (if (hovered) Hovered else 0UL) or
                    (if (pressed) Pressed else 0UL) or
                    (if (toggleableState != ToggleableState.Off) Selected else 0UL) or
                    (if (toggleableState == ToggleableState.Indeterminate) Indeterminate else 0UL) or
                    (if (active) Active else 0UL),
            )
    }
}
