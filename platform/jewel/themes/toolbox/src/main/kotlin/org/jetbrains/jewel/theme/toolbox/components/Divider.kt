package org.jetbrains.jewel.theme.toolbox.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.Orientation
import org.jetbrains.jewel.theme.toolbox.styles.DividerStyle
import org.jetbrains.jewel.theme.toolbox.styles.LocalDividerStyle

@Composable
fun Divider(
    modifier: Modifier = Modifier,
    orientation: Orientation = Orientation.Horizontal,
    style: DividerStyle = LocalDividerStyle.current,
    indent: Dp = 0.dp
) {
    val indentMod = if (indent.value != 0f) {
        Modifier.padding(start = indent)
    } else {
        Modifier
    }

    val orientationModifier = when (orientation) {
        Orientation.Horizontal -> Modifier.height(style.appearance.stroke.width).fillMaxWidth()
        Orientation.Vertical -> Modifier.width(style.appearance.stroke.width).fillMaxHeight()
    }

    Box(modifier.then(indentMod).then(orientationModifier).background(color = style.appearance.color))
}
