package org.jetbrains.jewel.components

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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.Orientation
import org.jetbrains.jewel.LocalPalette

@Composable
fun Divider(
    modifier: Modifier = Modifier,
    color: Color = LocalPalette.current.controlStroke,
    thickness: Dp = 1.dp,
    orientation: Orientation = Orientation.Horizontal,
    startIndent: Dp = 0.dp
) {
    val indentMod = if (startIndent.value != 0f) {
        Modifier.padding(start = startIndent)
    } else {
        Modifier
    }

    val orientationModifier = when (orientation) {
        Orientation.Horizontal -> Modifier.height(thickness).fillMaxWidth()
        Orientation.Vertical -> Modifier.width(thickness).fillMaxHeight()
    }

    Box(
        modifier
            .then(indentMod)
            .then(orientationModifier)
            .background(color = color)
    )
}
