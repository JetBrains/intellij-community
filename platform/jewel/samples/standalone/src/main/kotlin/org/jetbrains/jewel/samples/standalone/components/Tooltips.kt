package org.jetbrains.jewel.samples.standalone.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.GroupHeader
import org.jetbrains.jewel.Text
import org.jetbrains.jewel.Tooltip
import org.jetbrains.jewel.intui.standalone.IntUiTheme

@Composable
fun Tooltips() {
    GroupHeader("Tooltips")
    Tooltip(tooltip = {
        Text("This is a tooltip")
    }) {
        Text(modifier = Modifier.border(1.dp, IntUiTheme.globalColors.borders.normal).padding(4.dp), text = "Hover Me!")
    }
}
