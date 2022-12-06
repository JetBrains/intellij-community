package org.jetbrains.jewel.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.Orientation

@Composable
fun GroupHeader(text: String, modifier: Modifier = Modifier) {
    Row (modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(text)
        Divider(orientation = Orientation.Horizontal, modifier = Modifier.padding(5.dp))
    }
}
