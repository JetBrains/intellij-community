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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.styling.IconButtonStyle
import org.jetbrains.jewel.ui.component.styling.TooltipStyle
import org.jetbrains.jewel.ui.icon.IconKey
import org.jetbrains.jewel.ui.painter.PainterHint
import org.jetbrains.jewel.ui.theme.iconButtonStyle
import org.jetbrains.jewel.ui.theme.tooltipStyle

@Composable
public fun IconActionButton(
    key: IconKey,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier,
    enabled: Boolean = true,
    focusable: Boolean = true,
    style: IconButtonStyle = JewelTheme.iconButtonStyle,
    colorFilter: ColorFilter? = null,
    hint: PainterHint? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    iconClass: Class<*> = key.iconClass,
) {
    BaseIconActionButton(
        key = key,
        contentDescription = contentDescription,
        iconClass = iconClass,
        enabled = enabled,
        focusable = focusable,
        style = style,
        interactionSource = interactionSource,
        modifier = modifier,
        iconModifier = iconModifier,
        colorFilter = colorFilter,
        hint = hint,
        onClick = onClick,
    )
}

@Suppress("ComposableParamOrder") // To fix in JEWEL-924
@Composable
public fun IconActionButton(
    key: IconKey,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier,
    enabled: Boolean = true,
    focusable: Boolean = true,
    style: IconButtonStyle = JewelTheme.iconButtonStyle,
    colorFilter: ColorFilter? = null,
    hint: PainterHint? = null,
    tooltipStyle: TooltipStyle = JewelTheme.tooltipStyle,
    tooltipModifier: Modifier = Modifier,
    tooltipPlacement: TooltipPlacement = FixedCursorPoint(offset = DpOffset(0.dp, 16.dp)),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    iconClass: Class<*> = key.iconClass,
    tooltip: @Composable () -> Unit,
) {
    Tooltip(tooltip, style = tooltipStyle, modifier = tooltipModifier, tooltipPlacement = tooltipPlacement) {
        @Suppress("ModifierNotUsedAtRoot") // This is intentional
        BaseIconActionButton(
            key = key,
            contentDescription = contentDescription,
            iconClass = iconClass,
            enabled = enabled,
            focusable = focusable,
            style = style,
            interactionSource = interactionSource,
            modifier = modifier,
            iconModifier = iconModifier,
            colorFilter = colorFilter,
            hint = hint,
            onClick = onClick,
        )
    }
}

@Composable
public fun IconActionButton(
    key: IconKey,
    contentDescription: String?,
    hints: Array<PainterHint>,
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
    CoreIconActionButton(
        key = key,
        contentDescription = contentDescription,
        iconClass = iconClass,
        enabled = enabled,
        focusable = focusable,
        style = style,
        interactionSource = interactionSource,
        colorFilter = colorFilter,
        hints = hints,
        onClick = onClick,
        modifier = modifier,
        iconModifier = iconModifier,
    )
}

@Suppress("ComposableParamOrder") // To fix in JEWEL-924
@Composable
public fun IconActionButton(
    key: IconKey,
    contentDescription: String?,
    hints: Array<PainterHint>,
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
        CoreIconActionButton(
            key = key,
            modifier = modifier,
            iconModifier = iconModifier,
            contentDescription = contentDescription,
            iconClass = iconClass,
            enabled = enabled,
            focusable = focusable,
            style = style,
            interactionSource = interactionSource,
            colorFilter = colorFilter,
            hints = hints,
            onClick = onClick,
        )
    }
}

@Composable
private fun BaseIconActionButton(
    onClick: () -> Unit,
    key: IconKey,
    contentDescription: String?,
    iconClass: Class<*>,
    enabled: Boolean,
    focusable: Boolean,
    style: IconButtonStyle,
    interactionSource: MutableInteractionSource,
    colorFilter: ColorFilter?,
    hint: PainterHint?,
    modifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier,
) {
    if (hint != null) {
        CoreIconActionButton(
            key = key,
            contentDescription = contentDescription,
            iconClass = iconClass,
            enabled = enabled,
            focusable = focusable,
            style = style,
            interactionSource = interactionSource,
            colorFilter = colorFilter,
            hint = hint,
            onClick = onClick,
            modifier = modifier,
            iconModifier = iconModifier,
        )
    } else {
        CoreIconActionButton(
            key = key,
            contentDescription = contentDescription,
            iconClass = iconClass,
            enabled = enabled,
            focusable = focusable,
            style = style,
            interactionSource = interactionSource,
            colorFilter = colorFilter,
            hints = emptyArray(),
            onClick = onClick,
            modifier = modifier,
            iconModifier = iconModifier,
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
    colorFilter: ColorFilter?,
    hints: Array<PainterHint>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier,
) {
    IconButton(onClick, modifier, enabled, focusable, style, interactionSource) {
        Icon(
            key = key,
            contentDescription = contentDescription,
            modifier = iconModifier,
            iconClass = iconClass,
            colorFilter = colorFilter,
            hints = hints,
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
    colorFilter: ColorFilter?,
    hint: PainterHint,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier,
) {
    IconButton(onClick, modifier, enabled, focusable, style, interactionSource) {
        Icon(
            key = key,
            contentDescription = contentDescription,
            modifier = iconModifier,
            iconClass = iconClass,
            hint = hint,
            colorFilter = colorFilter,
        )
    }
}

@Composable
public fun IconActionButton(
    painter: Painter,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier,
    enabled: Boolean = true,
    focusable: Boolean = true,
    style: IconButtonStyle = JewelTheme.iconButtonStyle,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    CoreIconActionButton(
        painter = painter,
        contentDescription = contentDescription,
        enabled = enabled,
        focusable = focusable,
        style = style,
        interactionSource = interactionSource,
        onClick = onClick,
        modifier = modifier,
        iconModifier = iconModifier,
    )
}

@Suppress("ComposableParamOrder") // To fix in JEWEL-924
@Composable
public fun IconActionButton(
    painter: Painter,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier,
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
        @Suppress("ModifierNotUsedAtRoot") // This is intentional
        CoreIconActionButton(
            painter = painter,
            modifier = modifier,
            iconModifier = iconModifier,
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
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier,
) {
    IconButton(onClick, modifier, enabled, focusable, style, interactionSource) {
        Icon(painter, contentDescription, modifier = iconModifier)
    }
}
