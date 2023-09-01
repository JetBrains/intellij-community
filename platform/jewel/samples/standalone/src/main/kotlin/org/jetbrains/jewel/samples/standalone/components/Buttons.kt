package org.jetbrains.jewel.samples.standalone.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.DefaultButton
import org.jetbrains.jewel.GroupHeader
import org.jetbrains.jewel.OutlinedButton
import org.jetbrains.jewel.Text

@Composable
fun Buttons() {
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
    }
}
