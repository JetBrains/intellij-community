package org.jetbrains.jewel

import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.jewel.styling.GroupHeaderStyle
import org.jetbrains.jewel.styling.LocalGroupHeaderStyle

@Composable
fun GroupHeader(
    text: String,
    modifier: Modifier = Modifier,
    style: GroupHeaderStyle = LocalGroupHeaderStyle.current,
) {
    CompositionLocalProvider(
        LocalContentColor provides style.colors.content
    ) {
        Row(modifier, verticalAlignment = Alignment.CenterVertically) {
            Text(text)
            Divider(
                color = style.colors.divider,
                orientation = Orientation.Horizontal,
                startIndent = style.metrics.indent,
                thickness = style.metrics.dividerThickness
            )
        }
    }
}
