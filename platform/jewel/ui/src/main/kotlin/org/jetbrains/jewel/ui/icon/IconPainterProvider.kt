// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui.icon

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.painter.Painter
import org.jetbrains.icons.api.Icon

public interface IconPainterProvider {
    @Composable
    public fun getIconPainter(icon: Icon): Painter
}

public val LocalIconPainterProvider: ProvidableCompositionLocal<IconPainterProvider> =
    staticCompositionLocalOf {
        error("No LocalIconPainterProvider provided. Have you forgotten the theme?")
    }
