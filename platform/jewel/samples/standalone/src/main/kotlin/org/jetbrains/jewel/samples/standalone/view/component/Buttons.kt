package org.jetbrains.jewel.samples.standalone.view.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.samples.standalone.viewmodel.View
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.PlatformIcon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icons.AllIconsKeys

@Composable
@View(title = "Buttons", position = 0, icon = "icons/components/button.svg")
fun Buttons() {
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
            PlatformIcon(AllIconsKeys.Actions.Close, contentDescription = "IconButton")
        }
    }
}
