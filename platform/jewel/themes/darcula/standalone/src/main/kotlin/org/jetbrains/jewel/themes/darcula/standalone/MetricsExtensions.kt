package org.jetbrains.jewel.themes.darcula.standalone

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.IntelliJMetrics
import org.jetbrains.jewel.util.isMacOs

val IntelliJMetrics.Button.Companion.default
    get() = IntelliJMetrics.Button(
        strokeWidth = Dp.Hairline,
        arc = CornerSize(3.dp),
        padding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
    )

val IntelliJMetrics.Companion.default
    get() = IntelliJMetrics(
        singlePadding = 8.dp,
        controlFocusHaloWidth = 2.dp,
        controlFocusHaloArc = Dp.Hairline,
        controlArc = 3.dp,
        button = IntelliJMetrics.Button.default,
        separator = IntelliJMetrics.Separator.default,
        scrollbar = if (isMacOs()) IntelliJMetrics.Scrollbar.macOs else IntelliJMetrics.Scrollbar.default,
        treeView = IntelliJMetrics.TreeView.default
    )

val IntelliJMetrics.TreeView.Companion.default
    get() = IntelliJMetrics.TreeView(
        indentWidth = 24.dp,
        arrowEndPadding = 4.dp
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
    get() = IntelliJMetrics.Separator(strokeWidth = Dp.Hairline)
