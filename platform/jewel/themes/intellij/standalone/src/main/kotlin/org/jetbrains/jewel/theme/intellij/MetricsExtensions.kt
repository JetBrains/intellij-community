package org.jetbrains.jewel.theme.intellij

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.isMacOs

val IntelliJMetrics.Button.Companion.default
    get() = IntelliJMetrics.Button(
        strokeWidth = 1.dp,
        arc = CornerSize(6.dp),
        padding = PaddingValues(horizontal = 14.dp, vertical = 2.dp)
    )

val IntelliJMetrics.Companion.default
    get() = IntelliJMetrics(
        gridSize = 8.dp,
        singlePadding = 8.dp,
        doublePadding = 16.dp,
        controlFocusHaloWidth = 2.dp,
        controlFocusHaloArc = 1.dp,
        controlArc = 3.dp,
        button = IntelliJMetrics.Button.default,
        separator = IntelliJMetrics.Separator.default,
        scrollbar = if (isMacOs()) IntelliJMetrics.Scrollbar.macOs else IntelliJMetrics.Scrollbar.default
    )

val IntelliJMetrics.Scrollbar.Companion.default
    get() = IntelliJMetrics.Scrollbar(
        minSize = 13.dp, // myThickness * 2 (see DefaultScrollBarUI.updateThumbBounds)
        thickness = 13.dp, // myThickness
        thumbCornerSize = CornerSize(0.dp), // See com.intellij.ui.components.ScrollBarPainter.Thumb.paint
    )

val IntelliJMetrics.Scrollbar.Companion.macOs
    get() = IntelliJMetrics.Scrollbar(
        minSize = 13.dp, // myThickness * 2 (see DefaultScrollBarUI.updateThumbBounds)
        thickness = 14.dp, // myThickness
        thumbCornerSize = CornerSize(14.dp), // See com.intellij.ui.components.ScrollBarPainter.Thumb.paint
    )

val IntelliJMetrics.Separator.Companion.default
    get() = IntelliJMetrics.Separator(strokeWidth = 1.dp)
