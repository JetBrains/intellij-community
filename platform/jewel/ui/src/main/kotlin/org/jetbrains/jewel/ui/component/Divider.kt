package org.jetbrains.jewel.ui.component

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
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.styling.DividerStyle
import org.jetbrains.jewel.ui.theme.dividerStyle

@Composable
public fun Divider(
    orientation: Orientation,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    thickness: Dp = Dp.Unspecified,
    startIndent: Dp = Dp.Unspecified,
    style: DividerStyle = JewelTheme.dividerStyle,
) {
    val indentModifier =
        if (startIndent.value != 0f) {
            Modifier.padding(start = startIndent.takeOrElse { style.metrics.startIndent })
        } else {
            Modifier
        }

    val actualThickness = thickness.takeOrElse { style.metrics.thickness }
    val orientationModifier =
        when (orientation) {
            Orientation.Horizontal -> Modifier.height(actualThickness).fillMaxWidth()
            Orientation.Vertical -> Modifier.width(actualThickness).fillMaxHeight()
        }

    val lineColor = color.takeOrElse { style.color }
    Box(
        modifier.then(indentModifier)
            .then(orientationModifier)
            .background(color = lineColor),
    )
}
