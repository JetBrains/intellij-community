package org.jetbrains.jewel

import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.triStateToggleable
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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.res.ResourceLoader
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.TextStyle
import org.jetbrains.jewel.CommonStateBitMask.Active
import org.jetbrains.jewel.CommonStateBitMask.Enabled
import org.jetbrains.jewel.CommonStateBitMask.Focused
import org.jetbrains.jewel.CommonStateBitMask.Hovered
import org.jetbrains.jewel.CommonStateBitMask.Indeterminate
import org.jetbrains.jewel.CommonStateBitMask.Pressed
import org.jetbrains.jewel.CommonStateBitMask.Selected
import org.jetbrains.jewel.ToggleableComponentState.Companion.readToggleableState
import org.jetbrains.jewel.foundation.Stroke
import org.jetbrains.jewel.styling.CheckboxColors
import org.jetbrains.jewel.styling.CheckboxIcons
import org.jetbrains.jewel.styling.CheckboxMetrics
import org.jetbrains.jewel.styling.LocalCheckboxStyle

@Composable
fun Checkbox(
    checked: Boolean,
    resourceLoader: ResourceLoader,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    outline: Outline = Outline.None,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    colors: CheckboxColors = IntelliJTheme.checkboxStyle.colors,
    metrics: CheckboxMetrics = IntelliJTheme.checkboxStyle.metrics,
    icons: CheckboxIcons = IntelliJTheme.checkboxStyle.icons,
    textStyle: TextStyle = LocalTextStyle.current,
) {
    val state by remember { mutableStateOf(ToggleableState(checked)) }
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
        resourceLoader = resourceLoader,
        content = null,
    )
}

@Composable
fun TriStateCheckbox(
    state: ToggleableState,
    resourceLoader: ResourceLoader,
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
        resourceLoader = resourceLoader,
        content = null,
    )
}

@Composable
fun TriStateCheckboxRow(
    text: String,
    state: ToggleableState,
    resourceLoader: ResourceLoader,
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
        resourceLoader = resourceLoader,
        textStyle = textStyle,
    ) {
        Text(text)
    }
}

@Composable
fun CheckboxRow(
    text: String,
    checked: Boolean,
    resourceLoader: ResourceLoader,
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
        resourceLoader = resourceLoader,
        textStyle = textStyle,
    ) {
        Text(text)
    }
}

@Composable
fun CheckboxRow(
    checked: Boolean,
    resourceLoader: ResourceLoader,
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
        onClick = {
            onCheckedChange?.invoke(!checked)
        },
        modifier = modifier,
        enabled = enabled,
        outline = outline,
        interactionSource = interactionSource,
        colors = colors,
        metrics = metrics,
        icons = icons,
        resourceLoader = resourceLoader,
        textStyle = textStyle,
        content = content,
    )
}

@Composable
fun TriStateCheckboxRow(
    state: ToggleableState,
    resourceLoader: ResourceLoader,
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
        resourceLoader = resourceLoader,
        textStyle = textStyle,
        content = content,
    )
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun CheckboxImpl(
    state: ToggleableState,
    onClick: () -> Unit,
    colors: CheckboxColors,
    metrics: CheckboxMetrics,
    icons: CheckboxIcons,
    resourceLoader: ResourceLoader,
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
                is PressInteraction.Cancel, is PressInteraction.Release ->
                    checkboxState =
                        checkboxState.copy(pressed = false)

                is HoverInteraction.Enter -> checkboxState = checkboxState.copy(hovered = true)
                is HoverInteraction.Exit -> checkboxState = checkboxState.copy(hovered = true)
                is FocusInteraction.Focus -> checkboxState = checkboxState.copy(focused = true)
                is FocusInteraction.Unfocus -> checkboxState = checkboxState.copy(focused = false)
            }
        }
    }

    val wrapperModifier = modifier.triStateToggleable(
        state = state,
        onClick = onClick,
        enabled = enabled,
        role = Role.Checkbox,
        interactionSource = interactionSource,
        indication = null,
    )

    val checkBoxImageModifier = Modifier.size(metrics.checkboxSize)
        .outline(
            state = checkboxState,
            outline = outline,
            outlineShape = RoundedCornerShape(metrics.checkboxCornerSize),
            alignment = Stroke.Alignment.Center,
            outlineWidth = metrics.outlineWidth,
        )

    val checkboxPainter by icons.checkbox.getPainter(checkboxState, resourceLoader)

    if (content == null) {
        CheckBoxImage(wrapperModifier, checkboxPainter, checkBoxImageModifier)
    } else {
        Row(
            wrapperModifier,
            horizontalArrangement = Arrangement.spacedBy(metrics.iconContentGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CheckBoxImage(Modifier, checkboxPainter, checkBoxImageModifier)

            val contentColor by colors.contentFor(checkboxState)
            CompositionLocalProvider(
                LocalTextStyle provides textStyle.copy(color = contentColor.takeOrElse { textStyle.color }),
                LocalContentColor provides contentColor.takeOrElse { textStyle.color },
            ) {
                content()
            }
        }
    }
}

@Composable
private fun CheckBoxImage(outerModifier: Modifier, checkboxPainter: Painter, checkBoxModifier: Modifier) {
    Box(outerModifier, contentAlignment = Alignment.Center) {
        Image(checkboxPainter, contentDescription = null, modifier = checkBoxModifier)
    }
}

@Immutable
@JvmInline
value class CheckboxState(private val state: ULong) : ToggleableComponentState {

    @Stable
    override val toggleableState: ToggleableState
        get() = state.readToggleableState()

    @Stable
    override val isEnabled: Boolean
        get() = state and Enabled != 0UL

    @Stable
    override val isActive: Boolean
        get() = state and Active != 0UL

    @Stable
    override val isSelected: Boolean
        get() = toggleableState != ToggleableState.Off

    @Stable
    override val isFocused: Boolean
        get() = state and Focused != 0UL

    @Stable
    override val isHovered: Boolean
        get() = state and Hovered != 0UL

    @Stable
    override val isPressed: Boolean
        get() = state and Pressed != 0UL

    fun copy(
        toggleableState: ToggleableState = this.toggleableState,
        enabled: Boolean = isEnabled,
        focused: Boolean = isFocused,
        pressed: Boolean = isPressed,
        hovered: Boolean = isHovered,
        active: Boolean = isActive,
    ) = of(
        toggleableState = toggleableState,
        enabled = enabled,
        focused = focused,
        pressed = pressed,
        hovered = hovered,
        active = active,
    )

    override fun toString() =
        "${javaClass.simpleName}(toggleableState=$toggleableState, isEnabled=$isEnabled, isFocused=$isFocused, " +
            "isHovered=$isHovered, isPressed=$isPressed, isSelected=$isSelected, isActive=$isActive)"

    companion object {

        fun of(
            toggleableState: ToggleableState,
            enabled: Boolean = true,
            focused: Boolean = false,
            pressed: Boolean = false,
            hovered: Boolean = false,
            active: Boolean = false,
        ) = CheckboxState(
            state = (if (enabled) Enabled else 0UL) or
                (if (focused) Focused else 0UL) or
                (if (hovered) Hovered else 0UL) or
                (if (pressed) Pressed else 0UL) or
                (if (toggleableState != ToggleableState.Off) Selected else 0UL) or
                (if (toggleableState == ToggleableState.Indeterminate) Indeterminate else 0UL) or
                (if (active) Active else 0UL),
        )
    }
}
