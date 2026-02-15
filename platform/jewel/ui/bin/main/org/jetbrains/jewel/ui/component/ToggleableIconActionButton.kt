package org.jetbrains.jewel.ui.component

import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.styling.IconButtonStyle
import org.jetbrains.jewel.ui.component.styling.TooltipStyle
import org.jetbrains.jewel.ui.icon.IconKey
import org.jetbrains.jewel.ui.painter.PainterHint
import org.jetbrains.jewel.ui.painter.hints.Stroke
import org.jetbrains.jewel.ui.theme.iconButtonStyle
import org.jetbrains.jewel.ui.theme.tooltipStyle

@Composable
public fun ToggleableIconActionButton(
    key: IconKey,
    contentDescription: String?,
    value: Boolean,
    onValueChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier,
    enabled: Boolean = true,
    focusable: Boolean = true,
    style: IconButtonStyle = JewelTheme.iconButtonStyle,
    colorFilter: ColorFilter? = null,
    extraHint: PainterHint? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    iconClass: Class<*> = key.iconClass,
) {
    BaseToggleableIconActionButton(
        key = key,
        contentDescription = contentDescription,
        iconClass = iconClass,
        value = value,
        enabled = enabled,
        focusable = focusable,
        style = style,
        interactionSource = interactionSource,
        colorFilter = colorFilter,
        extraHint = extraHint,
        onValueChange = onValueChange,
        modifier = modifier,
        iconModifier = iconModifier,
    )
}

@Suppress("ComposableParamOrder") // To fix in JEWEL-932
@Composable
public fun ToggleableIconActionButton(
    key: IconKey,
    contentDescription: String?,
    value: Boolean,
    onValueChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier,
    enabled: Boolean = true,
    focusable: Boolean = true,
    style: IconButtonStyle = JewelTheme.iconButtonStyle,
    colorFilter: ColorFilter? = null,
    extraHint: PainterHint? = null,
    tooltipStyle: TooltipStyle = JewelTheme.tooltipStyle,
    tooltipModifier: Modifier = Modifier,
    tooltipPlacement: TooltipPlacement = FixedCursorPoint(offset = DpOffset(0.dp, 16.dp)),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    iconClass: Class<*> = key.iconClass,
    tooltip: @Composable () -> Unit,
) {
    Tooltip(tooltip, style = tooltipStyle, modifier = tooltipModifier, tooltipPlacement = tooltipPlacement) {
        @Suppress("ModifierNotUsedAtRoot") // This is intentional
        BaseToggleableIconActionButton(
            key = key,
            contentDescription = contentDescription,
            iconClass = iconClass,
            value = value,
            enabled = enabled,
            focusable = focusable,
            style = style,
            interactionSource = interactionSource,
            colorFilter = colorFilter,
            extraHint = extraHint,
            onValueChange = onValueChange,
            modifier = modifier,
            iconModifier = iconModifier,
        )
    }
}

@Composable
public fun ToggleableIconActionButton(
    key: IconKey,
    contentDescription: String?,
    value: Boolean,
    extraHints: Array<PainterHint>,
    onValueChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier,
    enabled: Boolean = true,
    focusable: Boolean = true,
    style: IconButtonStyle = JewelTheme.iconButtonStyle,
    colorFilter: ColorFilter? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    iconClass: Class<*> = key.iconClass,
) {
    CoreToggleableIconActionButton(
        key = key,
        contentDescription = contentDescription,
        iconClass = iconClass,
        value = value,
        enabled = enabled,
        focusable = focusable,
        style = style,
        interactionSource = interactionSource,
        extraHints = extraHints,
        onValueChange = onValueChange,
        modifier = modifier,
        iconModifier = iconModifier,
        colorFilter = colorFilter,
    )
}

@Suppress("ComposableParamOrder") // To fix in JEWEL-932
@Composable
public fun ToggleableIconActionButton(
    key: IconKey,
    contentDescription: String?,
    value: Boolean,
    extraHints: Array<PainterHint>,
    onValueChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier,
    enabled: Boolean = true,
    focusable: Boolean = true,
    style: IconButtonStyle = JewelTheme.iconButtonStyle,
    colorFilter: ColorFilter? = null,
    tooltipStyle: TooltipStyle = JewelTheme.tooltipStyle,
    tooltipModifier: Modifier = Modifier,
    tooltipPlacement: TooltipPlacement = FixedCursorPoint(offset = DpOffset(0.dp, 16.dp)),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    iconClass: Class<*> = key.iconClass,
    tooltip: @Composable () -> Unit,
) {
    Tooltip(tooltip, style = tooltipStyle, modifier = tooltipModifier, tooltipPlacement = tooltipPlacement) {
        @Suppress("ModifierNotUsedAtRoot") // This is intentional
        CoreToggleableIconActionButton(
            key = key,
            contentDescription = contentDescription,
            iconClass = iconClass,
            value = value,
            enabled = enabled,
            focusable = focusable,
            style = style,
            interactionSource = interactionSource,
            extraHints = extraHints,
            onValueChange = onValueChange,
            modifier = modifier,
            iconModifier = iconModifier,
            colorFilter = colorFilter,
        )
    }
}

@Composable
private fun BaseToggleableIconActionButton(
    key: IconKey,
    contentDescription: String?,
    iconClass: Class<*>,
    value: Boolean,
    enabled: Boolean,
    focusable: Boolean,
    style: IconButtonStyle,
    interactionSource: MutableInteractionSource,
    colorFilter: ColorFilter?,
    extraHint: PainterHint?,
    onValueChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier,
) {
    if (extraHint != null) {
        CoreToggleableIconActionButton(
            key = key,
            contentDescription = contentDescription,
            iconClass = iconClass,
            value = value,
            enabled = enabled,
            focusable = focusable,
            style = style,
            interactionSource = interactionSource,
            extraHint = extraHint,
            onValueChange = onValueChange,
            modifier = modifier,
            iconModifier = iconModifier,
            colorFilter = colorFilter,
        )
    } else {
        CoreToggleableIconActionButton(
            key = key,
            contentDescription = contentDescription,
            iconClass = iconClass,
            value = value,
            enabled = enabled,
            focusable = focusable,
            style = style,
            interactionSource = interactionSource,
            extraHints = emptyArray(),
            onValueChange = onValueChange,
            modifier = modifier,
            iconModifier = iconModifier,
            colorFilter = colorFilter,
        )
    }
}

@Composable
private fun CoreToggleableIconActionButton(
    key: IconKey,
    contentDescription: String?,
    iconClass: Class<*>,
    value: Boolean,
    enabled: Boolean,
    focusable: Boolean,
    style: IconButtonStyle,
    interactionSource: MutableInteractionSource,
    extraHints: Array<PainterHint>,
    onValueChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier,
    colorFilter: ColorFilter? = null,
) {
    ToggleableIconButton(value, onValueChange, modifier, enabled, focusable, style, interactionSource) {
        val strokeColor by style.colors.toggleableForegroundFor(it)
        Icon(
            key = key,
            contentDescription = contentDescription,
            iconClass = iconClass,
            modifier = iconModifier,
            hints = arrayOf(Stroke(strokeColor), *extraHints),
            colorFilter = colorFilter,
        )
    }
}

@Composable
private fun CoreToggleableIconActionButton(
    key: IconKey,
    contentDescription: String?,
    iconClass: Class<*>,
    value: Boolean,
    enabled: Boolean,
    focusable: Boolean,
    style: IconButtonStyle,
    interactionSource: MutableInteractionSource,
    extraHint: PainterHint,
    onValueChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier,
    colorFilter: ColorFilter? = null,
) {
    ToggleableIconButton(value, onValueChange, modifier, enabled, focusable, style, interactionSource) {
        val strokeColor by style.colors.toggleableForegroundFor(it)
        Icon(
            key = key,
            contentDescription = contentDescription,
            iconClass = iconClass,
            modifier = iconModifier,
            hints = arrayOf(Stroke(strokeColor), extraHint),
            colorFilter = colorFilter,
        )
    }
}
