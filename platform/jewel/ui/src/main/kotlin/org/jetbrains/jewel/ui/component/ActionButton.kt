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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.styling.IconButtonStyle
import org.jetbrains.jewel.ui.component.styling.TooltipStyle
import org.jetbrains.jewel.ui.theme.iconButtonStyle
import org.jetbrains.jewel.ui.theme.tooltipStyle

@Composable
public fun ActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    focusable: Boolean = true,
    style: IconButtonStyle = JewelTheme.iconButtonStyle,
    contentPadding: PaddingValues = PaddingValues(horizontal = 4.dp),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable () -> Unit,
) {
    CoreActionButton(onClick, enabled, focusable, style, interactionSource, modifier) {
        Box(Modifier.padding(contentPadding)) { content() }
    }
}

@Composable
public fun ActionButton(
    onClick: () -> Unit,
    tooltip: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    focusable: Boolean = true,
    style: IconButtonStyle = JewelTheme.iconButtonStyle,
    contentPadding: PaddingValues = PaddingValues(horizontal = 4.dp),
    tooltipStyle: TooltipStyle = JewelTheme.tooltipStyle,
    tooltipPlacement: TooltipPlacement = FixedCursorPoint(offset = DpOffset(0.dp, 16.dp)),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable () -> Unit,
) {
    Tooltip(tooltip, style = tooltipStyle, tooltipPlacement = tooltipPlacement, modifier = modifier) {
        CoreActionButton(
            onClick = onClick,
            enabled = enabled,
            focusable = focusable,
            style = style,
            interactionSource = interactionSource,
        ) {
            Box(Modifier.padding(contentPadding)) { content() }
        }
    }
}

@Composable
private fun CoreActionButton(
    onClick: () -> Unit,
    enabled: Boolean,
    focusable: Boolean,
    style: IconButtonStyle,
    interactionSource: MutableInteractionSource,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    IconButton(onClick, modifier, enabled, focusable, style, interactionSource) { content() }
}
