package org.jetbrains.jewel.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.Orientation
import org.jetbrains.jewel.styles.LocalSeparatorStyle
import org.jetbrains.jewel.styles.SeparatorStyle

@Composable
fun Separator(
    modifier: Modifier = Modifier,
    orientation: Orientation = Orientation.Horizontal,
    style: SeparatorStyle = LocalSeparatorStyle.current,
    indent: Dp = 0.dp
) {
    val indentMod = if (indent.value != 0f) {
        Modifier.padding(start = indent)
    } else {
        Modifier
    }

    val strokeWidth = style.appearance.stroke.width
    val orientationModifier = when (orientation) {
        Orientation.Horizontal -> Modifier.height(strokeWidth).fillMaxWidth()
        Orientation.Vertical -> Modifier.width(strokeWidth).fillMaxHeight()
    }

    Box(
        modifier.then(indentMod)
            .then(orientationModifier)
            .drawWithContent {
                when (orientation) {
                    Orientation.Horizontal -> {
                        val start = Offset(0f, strokeWidth.value / 2f)
                        val end = Offset(size.width, strokeWidth.value / 2f)
                        drawLine(style.appearance.stroke.brush, start, end, strokeWidth = style.appearance.stroke.width.value)
                    }
                    Orientation.Vertical -> {
                        val start = Offset(strokeWidth.value / 2f, 0f)
                        val end = Offset(strokeWidth.value / 2f, size.height)
                        drawLine(style.appearance.stroke.brush, start, end, strokeWidth = style.appearance.stroke.width.value)
                    }
                }
            }
    )
}
