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
public fun SelectableIconActionButton(
    key: IconKey,
    contentDescription: String?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    focusable: Boolean = true,
    style: IconButtonStyle = JewelTheme.iconButtonStyle,
    colorFilter: ColorFilter? = null,
    extraHint: PainterHint? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    iconClass: Class<*> = key::class.java,
) {
    BaseSelectableIconActionButton(
        key,
        contentDescription,
        iconClass,
        selected,
        enabled,
        focusable,
        style,
        interactionSource,
        modifier,
        colorFilter,
        extraHint,
        onClick,
    )
}

@Composable
public fun SelectableIconActionButton(
    key: IconKey,
    contentDescription: String?,
    selected: Boolean,
    onClick: () -> Unit,
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
        BaseSelectableIconActionButton(
            key = key,
            modifier = modifier,
            contentDescription = contentDescription,
            iconClass = iconClass,
            selected = selected,
            enabled = enabled,
            focusable = focusable,
            style = style,
            interactionSource = interactionSource,
            colorFilter = colorFilter,
            extraHint = extraHint,
            onClick = onClick,
        )
    }
}

@Composable
public fun SelectableIconActionButton(
    key: IconKey,
    contentDescription: String?,
    selected: Boolean,
    extraHints: Array<PainterHint>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    focusable: Boolean = true,
    style: IconButtonStyle = JewelTheme.iconButtonStyle,
    colorFilter: ColorFilter? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    iconClass: Class<*> = key::class.java,
) {
    CoreSelectableIconActionButton(
        key,
        contentDescription,
        iconClass,
        selected,
        enabled,
        focusable,
        style,
        interactionSource,
        modifier,
        colorFilter,
        extraHints,
        onClick,
    )
}

@Composable
public fun SelectableIconActionButton(
    key: IconKey,
    contentDescription: String?,
    selected: Boolean,
    extraHints: Array<PainterHint>,
    onClick: () -> Unit,
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
        CoreSelectableIconActionButton(
            key = key,
            modifier = modifier,
            contentDescription = contentDescription,
            iconClass = iconClass,
            selected = selected,
            enabled = enabled,
            focusable = focusable,
            style = style,
            interactionSource = interactionSource,
            colorFilter = colorFilter,
            extraHints = extraHints,
            onClick = onClick,
        )
    }
}

@Composable
private fun BaseSelectableIconActionButton(
    key: IconKey,
    contentDescription: String?,
    iconClass: Class<*>,
    selected: Boolean,
    enabled: Boolean,
    focusable: Boolean,
    style: IconButtonStyle,
    interactionSource: MutableInteractionSource,
    modifier: Modifier,
    colorFilter: ColorFilter?,
    extraHint: PainterHint?,
    onClick: () -> Unit,
) {
    if (extraHint != null) {
        CoreSelectableIconActionButton(
            key = key,
            contentDescription = contentDescription,
            iconClass = iconClass,
            selected = selected,
            enabled = enabled,
            focusable = focusable,
            style = style,
            interactionSource = interactionSource,
            modifier = modifier,
            colorFilter = colorFilter,
            extraHint = extraHint,
            onClick = onClick,
        )
    } else {
        CoreSelectableIconActionButton(
            key = key,
            contentDescription = contentDescription,
            iconClass = iconClass,
            selected = selected,
            enabled = enabled,
            focusable = focusable,
            style = style,
            interactionSource = interactionSource,
            modifier = modifier,
            colorFilter = colorFilter,
            extraHints = emptyArray(),
            onClick = onClick,
        )
    }
}

@Composable
private fun CoreSelectableIconActionButton(
    key: IconKey,
    contentDescription: String?,
    iconClass: Class<*>,
    selected: Boolean,
    enabled: Boolean,
    focusable: Boolean,
    style: IconButtonStyle,
    interactionSource: MutableInteractionSource,
    modifier: Modifier,
    colorFilter: ColorFilter?,
    extraHint: PainterHint,
    onClick: () -> Unit,
) {
    SelectableIconButton(selected, onClick, modifier, enabled, focusable, style, interactionSource) {
        val strokeColor by style.colors.selectableForegroundFor(it)
        Icon(
            key,
            contentDescription,
            iconClass = iconClass,
            hints = arrayOf(Stroke(strokeColor), extraHint),
            colorFilter = colorFilter,
        )
    }
}

@Composable
private fun CoreSelectableIconActionButton(
    key: IconKey,
    contentDescription: String?,
    iconClass: Class<*>,
    selected: Boolean,
    enabled: Boolean,
    focusable: Boolean,
    style: IconButtonStyle,
    interactionSource: MutableInteractionSource,
    modifier: Modifier,
    colorFilter: ColorFilter?,
    extraHints: Array<PainterHint>,
    onClick: () -> Unit,
) {
    SelectableIconButton(selected, onClick, modifier, enabled, focusable, style, interactionSource) {
        val strokeColor by style.colors.selectableForegroundFor(it)
        Icon(
            key,
            contentDescription,
            iconClass = iconClass,
            hints = arrayOf(Stroke(strokeColor), *extraHints),
            colorFilter = colorFilter,
        )
    }
}
