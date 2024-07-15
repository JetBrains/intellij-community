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
package org.jetbrains.jewel.ui.component.styling

import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.FixedCursorPoint
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.Tooltip
import org.jetbrains.jewel.ui.icon.IconKey
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
    tooltipStyle: TooltipStyle = JewelTheme.tooltipStyle,
    tooltipPlacement: TooltipPlacement = FixedCursorPoint(offset = DpOffset(0.dp, 16.dp)),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    iconClass: Class<*> = key::class.java,
    tooltip: @Composable () -> Unit,
) {
    Tooltip(
        tooltip,
        style = tooltipStyle,
        tooltipPlacement = tooltipPlacement,
        modifier = modifier,
    ) {
        CoreIconActionButton(
            key = key,
            contentDescription = contentDescription,
            iconClass = iconClass,
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
    key: IconKey,
    contentDescription: String?,
    iconClass: Class<*>,
    enabled: Boolean,
    focusable: Boolean,
    style: IconButtonStyle,
    interactionSource: MutableInteractionSource,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    IconButton(onClick, modifier, enabled, focusable, style, interactionSource) {
        Icon(key, contentDescription, iconClass = iconClass)
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
    CoreIconActionButton(
        painter,
        contentDescription,
        enabled,
        focusable,
        style,
        interactionSource,
        modifier,
        onClick,
    )
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
    tooltipPlacement: TooltipPlacement = FixedCursorPoint(offset = DpOffset(0.dp, 16.dp)),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    tooltip: @Composable () -> Unit,
) {
    Tooltip(
        tooltip,
        style = tooltipStyle,
        tooltipPlacement = tooltipPlacement,
        modifier = modifier,
    ) {
        CoreIconActionButton(
            painter = painter,
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
    IconButton(onClick, modifier, enabled, focusable, style, interactionSource) {
        Icon(painter, contentDescription)
    }
}
