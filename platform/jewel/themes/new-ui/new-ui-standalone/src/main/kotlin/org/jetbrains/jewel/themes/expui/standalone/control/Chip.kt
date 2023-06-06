package org.jetbrains.jewel.themes.expui.standalone.control

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Suppress("MagicNumber")
@Composable
fun BaseChip(bgColor: Color, content: @Composable () -> Unit) {
    var hover by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .padding(vertical = 4.dp, horizontal = 4.dp)
            .defaultMinSize(40.dp)
            .clip(RoundedCornerShape(100))
            .background(if (!hover) bgColor else bgColor.copy(0.8f))
            .onHover { hover = it },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        content()
    }
}

@Composable
fun Chip(bgColor: Color, toolTipContent: (@Composable () -> Unit)? = null, chipContent: @Composable () -> Unit) {
    toolTipContent?.let {
        Tooltip(tooltip = {
            Box(
                modifier = Modifier
                    .padding(vertical = 8.dp, horizontal = 8.dp)
            ) {
                toolTipContent()
            }
        }) {
            BaseChip(bgColor, chipContent)
        }
    } ?: BaseChip(bgColor, chipContent)
}
