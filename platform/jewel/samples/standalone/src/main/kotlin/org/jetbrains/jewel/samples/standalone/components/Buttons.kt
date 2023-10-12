package org.jetbrains.jewel.samples.standalone.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.ResourceLoader
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.DefaultButton
import org.jetbrains.jewel.GroupHeader
import org.jetbrains.jewel.Icon
import org.jetbrains.jewel.IconButton
import org.jetbrains.jewel.JewelSvgLoader
import org.jetbrains.jewel.OutlinedButton
import org.jetbrains.jewel.Text
import org.jetbrains.jewel.styling.ResourcePainterProvider

@Composable
fun Buttons(svgLoader: JewelSvgLoader, resourceLoader: ResourceLoader) {
    GroupHeader("Buttons")
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(onClick = { }) {
            Text("Outlined")
        }

        OutlinedButton(onClick = {}, enabled = false) {
            Text("Outlined Disabled")
        }

        DefaultButton(onClick = {}) {
            Text("Default")
        }

        DefaultButton(onClick = {}, enabled = false) {
            Text("Default disabled")
        }

        IconButton(onClick = {}) {
            val iconProvider = remember { ResourcePainterProvider.stateless("icons/close.svg", svgLoader) }
            val iconPainter by iconProvider.getPainter(resourceLoader)
            Icon(
                painter = iconPainter,
                "icon",
            )
        }
    }
}
