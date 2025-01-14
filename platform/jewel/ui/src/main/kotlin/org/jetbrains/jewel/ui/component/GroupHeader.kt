package org.jetbrains.jewel.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.styling.GroupHeaderStyle
import org.jetbrains.jewel.ui.component.styling.LocalGroupHeaderStyle

/**
 * A component that displays a header for a group of items, with a title and optional slots on both sides.
 *
 * **Guidelines:** [on IJP SDK webhelp](https://plugins.jetbrains.com/docs/intellij/group-header.html)
 *
 * **Usage example:**
 * [`Borders.kt`](https://github.com/JetBrains/intellij-community/blob/master/platform/jewel/samples/standalone/src/main/kotlin/org/jetbrains/jewel/samples/standalone/view/component/Borders.kt)
 *
 * **Swing equivalent:**
 * [`TitledSeparator`](https://github.com/JetBrains/intellij-community/blob/master/platform/platform-api/src/com/intellij/ui/TitledSeparator.java)
 *
 * @param text The text to display in the header.
 * @param modifier The modifier to apply to the header.
 * @param startComponent The component to display on the left side of the header.
 * @param endComponent The component to display on the right side of the header.
 * @param style The style to apply to the header.
 * @see com.intellij.ui.TitledSeparator
 */
@Composable
public fun GroupHeader(
    text: String,
    modifier: Modifier = Modifier,
    startComponent: (@Composable () -> Unit)? = null,
    endComponent: (@Composable () -> Unit)? = null,
    style: GroupHeaderStyle = LocalGroupHeaderStyle.current,
    textStyle: TextStyle = JewelTheme.defaultTextStyle,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (startComponent != null) {
            Box(Modifier.size(16.dp)) { startComponent() }
        }
        Text(text, style = textStyle)

        Divider(
            orientation = Orientation.Horizontal,
            modifier = Modifier.weight(1f),
            color = style.colors.divider,
            thickness = style.metrics.dividerThickness,
            startIndent = style.metrics.indent,
        )
        if (endComponent != null) {
            Row(Modifier.height(16.dp)) { endComponent() }
        }
    }
}
