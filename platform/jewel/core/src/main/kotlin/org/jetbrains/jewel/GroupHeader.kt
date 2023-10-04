package org.jetbrains.jewel

import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import org.jetbrains.jewel.styling.GroupHeaderStyle
import org.jetbrains.jewel.styling.LocalGroupHeaderStyle

@Composable
fun GroupHeader(
    text: String,
    modifier: Modifier = Modifier,
    textColor: Color = Color.Unspecified,
    style: GroupHeaderStyle = LocalGroupHeaderStyle.current,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(text, color = textColor)
        Divider(
            color = style.colors.divider,
            orientation = Orientation.Horizontal,
            startIndent = style.metrics.indent,
            thickness = style.metrics.dividerThickness,
        )
    }
}
