package org.jetbrains.jewel.samples.standalone.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.DropdownLink
import org.jetbrains.jewel.ExternalLink
import org.jetbrains.jewel.GroupHeader
import org.jetbrains.jewel.Link
import org.jetbrains.jewel.Text
import org.jetbrains.jewel.separator

@Composable
fun Links() {
    GroupHeader("Links")

    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Link("Link", {})

        ExternalLink("ExternalLink", {})

        val items = remember {
            listOf(
                "Light",
                "Dark",
                "---",
                "High Contrast",
                "Darcula",
                "IntelliJ Light",
            )
        }
        var selected by remember { mutableStateOf(items.first()) }
        DropdownLink("DropdownLink") {
            items.forEach {
                if (it == "---") {
                    separator()
                } else {
                    selectableItem(selected == it, {
                        selected = it
                    }) {
                        Text(it)
                    }
                }
            }
        }
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Link("Link", {}, enabled = false)

        ExternalLink("ExternalLink", {}, enabled = false)

        DropdownLink("DropdownLink", enabled = false) {
        }
    }
}
