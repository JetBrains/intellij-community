package org.jetbrains.jewel.ui.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.styling.GroupHeaderStyle
import org.jetbrains.jewel.ui.component.styling.LocalGroupHeaderStyle

@Composable
public fun GroupHeader(
    text: String,
    modifier: Modifier = Modifier,
    textColor: Color = Color.Unspecified,
    style: GroupHeaderStyle = LocalGroupHeaderStyle.current,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(text, color = textColor)

        Divider(
            orientation = Orientation.Horizontal,
            modifier = Modifier.fillMaxWidth(),
            color = style.colors.divider,
            thickness = style.metrics.dividerThickness,
            startIndent = style.metrics.indent,
        )
    }
}
