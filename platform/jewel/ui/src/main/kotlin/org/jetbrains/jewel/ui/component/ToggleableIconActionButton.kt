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
    enabled: Boolean = true,
    focusable: Boolean = true,
    style: IconButtonStyle = JewelTheme.iconButtonStyle,
    colorFilter: ColorFilter? = null,
    extraHint: PainterHint? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    iconClass: Class<*> = key::class.java,
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
        modifier = modifier,
        colorFilter = colorFilter,
        extraHint = extraHint,
        onValueChange = onValueChange,
    )
}

@Composable
public fun ToggleableIconActionButton(
    key: IconKey,
    contentDescription: String?,
    value: Boolean,
    onValueChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    focusable: Boolean = true,
    style: IconButtonStyle = JewelTheme.iconButtonStyle,
    colorFilter: ColorFilter? = null,
    extraHint: PainterHint? = null,
    tooltipStyle: TooltipStyle = JewelTheme.tooltipStyle,
    tooltipModifier: Modifier = Modifier,
    tooltipPlacement: TooltipPlacement = FixedCursorPoint(offset = DpOffset(0.dp, 16.dp)),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    iconClass: Class<*> = key::class.java,
    tooltip: @Composable () -> Unit,
) {
    Tooltip(tooltip, style = tooltipStyle, modifier = tooltipModifier, tooltipPlacement = tooltipPlacement) {
        BaseToggleableIconActionButton(
            key = key,
            modifier = modifier,
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
    enabled: Boolean = true,
    focusable: Boolean = true,
    style: IconButtonStyle = JewelTheme.iconButtonStyle,
    colorFilter: ColorFilter? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    iconClass: Class<*> = key::class.java,
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
        modifier = modifier,
        colorFilter = colorFilter,
        extraHints = extraHints,
        onValueChange = onValueChange,
    )
}

@Composable
public fun ToggleableIconActionButton(
    key: IconKey,
    contentDescription: String?,
    value: Boolean,
    extraHints: Array<PainterHint>,
    onValueChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    focusable: Boolean = true,
    style: IconButtonStyle = JewelTheme.iconButtonStyle,
    colorFilter: ColorFilter? = null,
    tooltipStyle: TooltipStyle = JewelTheme.tooltipStyle,
    tooltipModifier: Modifier = Modifier,
    tooltipPlacement: TooltipPlacement = FixedCursorPoint(offset = DpOffset(0.dp, 16.dp)),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    iconClass: Class<*> = key::class.java,
    tooltip: @Composable () -> Unit,
) {
    Tooltip(tooltip, style = tooltipStyle, modifier = tooltipModifier, tooltipPlacement = tooltipPlacement) {
        CoreToggleableIconActionButton(
            key = key,
            modifier = modifier,
            contentDescription = contentDescription,
            iconClass = iconClass,
            value = value,
            enabled = enabled,
            focusable = focusable,
            style = style,
            interactionSource = interactionSource,
            colorFilter = colorFilter,
            extraHints = extraHints,
            onValueChange = onValueChange,
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
    modifier: Modifier,
    colorFilter: ColorFilter?,
    extraHint: PainterHint?,
    onValueChange: (Boolean) -> Unit,
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
            modifier = modifier,
            colorFilter = colorFilter,
            extraHint = extraHint,
            onValueChange = onValueChange,
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
            modifier = modifier,
            colorFilter = colorFilter,
            extraHints = emptyArray(),
            onValueChange = onValueChange,
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
    modifier: Modifier,
    colorFilter: ColorFilter? = null,
    extraHints: Array<PainterHint>,
    onValueChange: (Boolean) -> Unit,
) {
    ToggleableIconButton(value, onValueChange, modifier, enabled, focusable, style, interactionSource) {
        val strokeColor by style.colors.toggleableForegroundFor(it)
        Icon(
            key = key,
            contentDescription = contentDescription,
            iconClass = iconClass,
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
    modifier: Modifier,
    colorFilter: ColorFilter? = null,
    extraHint: PainterHint,
    onValueChange: (Boolean) -> Unit,
) {
    ToggleableIconButton(value, onValueChange, modifier, enabled, focusable, style, interactionSource) {
        val strokeColor by style.colors.toggleableForegroundFor(it)
        Icon(
            key = key,
            contentDescription = contentDescription,
            iconClass = iconClass,
            hints = arrayOf(Stroke(strokeColor), extraHint),
            colorFilter = colorFilter,
        )
    }
}
