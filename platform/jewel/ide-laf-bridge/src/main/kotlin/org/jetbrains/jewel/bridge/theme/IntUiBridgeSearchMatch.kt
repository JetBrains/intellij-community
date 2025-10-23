// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.bridge.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.intellij.util.ui.UIUtil
import org.jetbrains.jewel.bridge.toComposeColor
import org.jetbrains.jewel.ui.component.styling.SearchMatchColors
import org.jetbrains.jewel.ui.component.styling.SearchMatchMetrics
import org.jetbrains.jewel.ui.component.styling.SearchMatchStyle

internal fun readSearchMatchStyle(isDark: Boolean) =
    SearchMatchStyle(
        colors =
            SearchMatchColors(
                startBackground = UIUtil.getSearchMatchGradientStartColor().toComposeColor(),
                endBackground = UIUtil.getSearchMatchGradientEndColor().toComposeColor(),
                foreground = if (isDark) Color(0xFF000000) else Color(0xFF323232),
            ),
        metrics = SearchMatchMetrics(CornerSize(2.5.dp), PaddingValues(2.dp)),
    )
