// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.bridge.theme

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.ui.component.styling.OnOffButtonIcons
import org.jetbrains.jewel.ui.component.styling.OnOffButtonMetrics
import org.jetbrains.jewel.ui.component.styling.OnOffButtonStyle
import org.jetbrains.jewel.ui.icon.PathIconKey

// BridgePainterHintsProvider recolors the SVGs with the theme's ColorPalette.toggle-* colors.
internal fun readDefaultOnOffButtonStyle(): OnOffButtonStyle =
    OnOffButtonStyle(
        metrics = OnOffButtonMetrics(size = DpSize(32.dp, 22.dp)),
        icons =
            OnOffButtonIcons(
                onOffButton = PathIconKey("com/intellij/ide/ui/laf/icons/toggle.svg", OnOffButtonIcons::class.java)
            ),
    )
