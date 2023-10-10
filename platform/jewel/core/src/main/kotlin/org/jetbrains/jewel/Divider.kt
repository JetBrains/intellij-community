package org.jetbrains.jewel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.takeOrElse
import org.jetbrains.jewel.styling.DividerStyle

@Composable
fun Divider(
    orientation: Orientation,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    thickness: Dp = Dp.Unspecified,
    startIndent: Dp = Dp.Unspecified,
    style: DividerStyle = IntelliJTheme.dividerStyle,
) {
    val indentMod = if (startIndent.value != 0f) {
        Modifier.padding(start = startIndent.takeOrElse { style.metrics.startIndent })
    } else {
        Modifier
    }

    val actualThickness = thickness.takeOrElse { style.metrics.thickness }
    val orientationModifier = when (orientation) {
        Orientation.Horizontal -> Modifier.height(actualThickness).fillMaxWidth()
        Orientation.Vertical -> Modifier.width(actualThickness).fillMaxHeight()
    }

    val lineColor = color.takeOrElse { style.color }
    Box(
        modifier
            .then(indentMod)
            .then(orientationModifier)
            .background(color = lineColor),
    )
}
