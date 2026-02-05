// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui.icon

import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.util.fastRoundToInt
import org.jetbrains.icons.rendering.ImageModifiers
import org.jetbrains.icons.rendering.ImageResource

internal class ComposePainterImageResource(
    val painter: Painter,
    val modifiers: ImageModifiers?
): ImageResource {
    override val width: Int = painter.intrinsicSize.width.fastRoundToInt()
    override val height: Int = painter.intrinsicSize.height.fastRoundToInt()
}
