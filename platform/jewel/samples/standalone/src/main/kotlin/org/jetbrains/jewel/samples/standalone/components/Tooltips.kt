package org.jetbrains.jewel.samples.standalone.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.GroupHeader
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Tooltip

@Composable
fun Tooltips() {
    GroupHeader("Tooltips")
    Tooltip(tooltip = {
        Text("This is a tooltip")
    }) {
        Text(modifier = Modifier.border(1.dp, JewelTheme.globalColors.borders.normal).padding(4.dp), text = "Hover Me!")
    }
}
