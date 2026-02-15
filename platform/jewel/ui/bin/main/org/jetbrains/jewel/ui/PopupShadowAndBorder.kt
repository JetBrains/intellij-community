// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.JewelFlags
import org.jetbrains.jewel.foundation.Stroke
import org.jetbrains.jewel.foundation.modifier.border
import org.jetbrains.jewel.foundation.modifier.thenIf

internal fun Modifier.popupShadowAndBorder(
    shape: Shape,
    shadowSize: Dp,
    shadowColor: Color,
    borderWidth: Dp,
    borderColor: Color,
): Modifier =
    thenIf(!JewelFlags.useCustomPopupRenderer) {
        dropShadow(shape, Shadow(radius = shadowSize, color = shadowColor, offset = DpOffset(0.dp, shadowSize / 2)))
            .border(Stroke.Alignment.Inside, borderWidth, borderColor, shape)
    }
