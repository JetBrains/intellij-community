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
import org.jetbrains.jewel.GroupHeader
import org.jetbrains.jewel.LocalResourceLoader
import org.jetbrains.jewel.Outline
import org.jetbrains.jewel.RadioButtonRow

@Composable
fun RadioButtons() {
    GroupHeader("RadioButtons")
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val resourceLoader = LocalResourceLoader.current
        var index by remember { mutableStateOf(0) }
        RadioButtonRow(
            text = "Default",
            selected = index == 0,
            onClick = { index = 0 },
            resourceLoader = resourceLoader
        )

        RadioButtonRow(
            text = "Error",
            selected = index == 1,
            onClick = { index = 1 },
            outline = Outline.Error,
            resourceLoader = resourceLoader
        )

        RadioButtonRow(
            text = "Disabled",
            selected = index == 2,
            onClick = { index = 2 },
            enabled = false,
            resourceLoader = resourceLoader
        )

        RadioButtonRow(
            text = "Another",
            selected = index == 3,
            onClick = { index = 3 },
            resourceLoader = resourceLoader
        )
    }
}
