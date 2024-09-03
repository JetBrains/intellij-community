/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.jewel.ui.component

import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
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
public fun IconActionButton(
    key: IconKey,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    focusable: Boolean = true,
    style: IconButtonStyle = JewelTheme.iconButtonStyle,
    hints: Array<PainterHint> = emptyArray(),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    iconClass: Class<*> = key::class.java,
) {
    CoreIconActionButton(
        key,
        contentDescription,
        iconClass,
        enabled,
        focusable,
        style,
        interactionSource,
        modifier,
        hints = hints,
        onClick,
    )
}

@Composable
public fun IconActionButton(
    key: IconKey,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    focusable: Boolean = true,
    style: IconButtonStyle = JewelTheme.iconButtonStyle,
    hints: Array<PainterHint> = emptyArray(),
    tooltipStyle: TooltipStyle = JewelTheme.tooltipStyle,
    tooltipModifier: Modifier = Modifier,
    tooltipPlacement: TooltipPlacement = FixedCursorPoint(offset = DpOffset(0.dp, 16.dp)),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    iconClass: Class<*> = key::class.java,
    tooltip: @Composable () -> Unit,
) {
    Tooltip(tooltip, style = tooltipStyle, modifier = tooltipModifier, tooltipPlacement = tooltipPlacement) {
        CoreIconActionButton(
            key = key,
            modifier = modifier,
            contentDescription = contentDescription,
            iconClass = iconClass,
            enabled = enabled,
            focusable = focusable,
            style = style,
            interactionSource = interactionSource,
            hints = hints,
            onClick = onClick,
        )
    }
}

@Composable
private fun CoreIconActionButton(
    key: IconKey,
    contentDescription: String?,
    iconClass: Class<*>,
    enabled: Boolean,
    focusable: Boolean,
    style: IconButtonStyle,
    interactionSource: MutableInteractionSource,
    modifier: Modifier,
    hints: Array<PainterHint>,
    onClick: () -> Unit,
) {
    IconButton(onClick, modifier, enabled, focusable, style, interactionSource) {
        Icon(key, contentDescription, iconClass = iconClass, hints = hints)
    }
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
    extraHints: Array<PainterHint> = emptyArray(),
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
        extraHints,
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
    extraHints: Array<PainterHint> = emptyArray(),
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
            extraHints = extraHints,
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
    extraHints: Array<PainterHint>,
    onClick: () -> Unit,
) {
    SelectableIconButton(selected, onClick, modifier, enabled, focusable, style, interactionSource) {
        val strokeColor by style.colors.selectableForegroundFor(it)
        Icon(key, contentDescription, iconClass = iconClass, hints = arrayOf(Stroke(strokeColor), *extraHints))
    }
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
    extraHints: Array<PainterHint> = emptyArray(),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    iconClass: Class<*> = key::class.java,
) {
    CoreToggleableIconActionButton(
        key,
        contentDescription,
        iconClass,
        value,
        enabled,
        focusable,
        style,
        interactionSource,
        modifier,
        extraHints,
        onValueChange,
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
    extraHints: Array<PainterHint> = emptyArray(),
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
            extraHints = extraHints,
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
    extraHints: Array<PainterHint>,
    onValueChange: (Boolean) -> Unit,
) {
    ToggleableIconButton(value, onValueChange, modifier, enabled, focusable, style, interactionSource) {
        val strokeColor by style.colors.toggleableForegroundFor(it)
        Icon(key, contentDescription, iconClass = iconClass, hints = arrayOf(Stroke(strokeColor), *extraHints))
    }
}

@Composable
public fun IconActionButton(
    painter: Painter,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    focusable: Boolean = true,
    style: IconButtonStyle = JewelTheme.iconButtonStyle,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    CoreIconActionButton(painter, contentDescription, enabled, focusable, style, interactionSource, modifier, onClick)
}

@Composable
public fun IconActionButton(
    painter: Painter,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    focusable: Boolean = true,
    style: IconButtonStyle = JewelTheme.iconButtonStyle,
    tooltipStyle: TooltipStyle = JewelTheme.tooltipStyle,
    tooltipModifier: Modifier = Modifier,
    tooltipPlacement: TooltipPlacement = FixedCursorPoint(offset = DpOffset(0.dp, 16.dp)),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    tooltip: @Composable () -> Unit,
) {
    Tooltip(tooltip, style = tooltipStyle, modifier = tooltipModifier, tooltipPlacement = tooltipPlacement) {
        CoreIconActionButton(
            painter = painter,
            modifier = modifier,
            contentDescription = contentDescription,
            enabled = enabled,
            focusable = focusable,
            style = style,
            interactionSource = interactionSource,
            onClick = onClick,
        )
    }
}

@Composable
private fun CoreIconActionButton(
    painter: Painter,
    contentDescription: String?,
    enabled: Boolean,
    focusable: Boolean,
    style: IconButtonStyle,
    interactionSource: MutableInteractionSource,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    IconButton(onClick, modifier, enabled, focusable, style, interactionSource) { Icon(painter, contentDescription) }
}
