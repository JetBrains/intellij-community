package org.jetbrains.jewel.samples.standalone.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.DefaultButton
import org.jetbrains.jewel.GroupHeader
import org.jetbrains.jewel.OutlinedButton
import org.jetbrains.jewel.Text

@Composable
fun ColumnScope.Buttons() {
    GroupHeader("Buttons")
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton({
        }) {
            Text("Cancel")
        }
        OutlinedButton({
        }) {
            Text("Apply")
        }
        OutlinedButton({}, enabled = false) {
            Text("Disabled")
        }
        DefaultButton(
            {
            },
            interactionSource = remember {
                MutableInteractionSource()
            }
        ) {
            Text("OK")
        }
    }
}
