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
    iconModifier: Modifier = Modifier,
    enabled: Boolean = true,
    focusable: Boolean = true,
    style: IconButtonStyle = JewelTheme.iconButtonStyle,
    colorFilter: ColorFilter? = null,
    extraHint: PainterHint? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    iconClass: Class<*> = key.iconClass,
) {
    BaseSelectableIconActionButton(
        key = key,
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
        modifier = modifier,
        iconModifier = iconModifier,
    )
}

@Suppress("ComposableParamOrder") // To fix in JEWEL-930
@Composable
public fun SelectableIconActionButton(
    key: IconKey,
    contentDescription: String?,
    selected: Boolean,
    onClick: () -> Unit,
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
        BaseSelectableIconActionButton(
            key = key,
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
            modifier = modifier,
            iconModifier = iconModifier,
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
    iconModifier: Modifier = Modifier,
    enabled: Boolean = true,
    focusable: Boolean = true,
    style: IconButtonStyle = JewelTheme.iconButtonStyle,
    colorFilter: ColorFilter? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    iconClass: Class<*> = key.iconClass,
) {
    CoreSelectableIconActionButton(
        key = key,
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
        modifier = modifier,
        iconModifier = iconModifier,
    )
}

@Suppress("ComposableParamOrder") // To fix in JEWEL-930
@Composable
public fun SelectableIconActionButton(
    key: IconKey,
    contentDescription: String?,
    selected: Boolean,
    extraHints: Array<PainterHint>,
    onClick: () -> Unit,
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
        CoreSelectableIconActionButton(
            key = key,
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
            modifier = modifier,
            iconModifier = iconModifier,
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
    colorFilter: ColorFilter?,
    extraHint: PainterHint?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier,
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
            colorFilter = colorFilter,
            extraHint = extraHint,
            onClick = onClick,
            modifier = modifier,
            iconModifier = iconModifier,
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
            colorFilter = colorFilter,
            extraHints = emptyArray(),
            onClick = onClick,
            modifier = modifier,
            iconModifier = iconModifier,
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
    colorFilter: ColorFilter?,
    extraHint: PainterHint,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier,
) {
    SelectableIconButton(selected, onClick, modifier, enabled, focusable, style, interactionSource) {
        val strokeColor by style.colors.selectableForegroundFor(it)
        Icon(
            key = key,
            contentDescription = contentDescription,
            modifier = iconModifier,
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
    colorFilter: ColorFilter?,
    extraHints: Array<PainterHint>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier,
) {
    SelectableIconButton(selected, onClick, modifier, enabled, focusable, style, interactionSource) {
        val strokeColor by style.colors.selectableForegroundFor(it)
        Icon(
            key = key,
            contentDescription = contentDescription,
            modifier = iconModifier,
            iconClass = iconClass,
            hints = arrayOf(Stroke(strokeColor), *extraHints),
            colorFilter = colorFilter,
        )
    }
}
