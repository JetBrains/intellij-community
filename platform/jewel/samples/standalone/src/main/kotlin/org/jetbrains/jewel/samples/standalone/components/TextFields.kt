package org.jetbrains.jewel.samples.standalone.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.samples.standalone.StandaloneSampleIcons
import org.jetbrains.jewel.ui.Outline
import org.jetbrains.jewel.ui.component.GroupHeader
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.LabelledTextField
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField

@Composable
fun TextFields() {
    GroupHeader("TextFields")
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        var text1 by remember { mutableStateOf("TextField") }
        TextField(text1, { text1 = it })

        var text2 by remember { mutableStateOf("") }
        TextField(text2, { text2 = it }, placeholder = { Text("Placeholder") })

        var text3 by remember { mutableStateOf("") }
        TextField(text3, { text3 = it }, outline = Outline.Error, placeholder = { Text("Error outline") })

        var text4 by remember { mutableStateOf("") }
        TextField(text4, { text4 = it }, outline = Outline.Warning, placeholder = { Text("Warning outline") })

        var text5 by remember { mutableStateOf("Disabled") }
        TextField(text5, { text5 = it }, enabled = false)
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        var text1 by remember { mutableStateOf("") }
        LabelledTextField(
            value = text1,
            onValueChange = { text1 = it },
            label = { Text("Label:") },
            placeholder = { Text("Labelled TextField") },
        )

        var text2 by remember { mutableStateOf("") }
        LabelledTextField(
            value = text2,
            onValueChange = { text2 = it },
            label = { Text("Label:") },
            hint = { Text("Attached hint text") },
            placeholder = { Text("Labelled TextField with hint") },
        )
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        var text by remember { mutableStateOf("With leading icon") }
        TextField(text, { text = it }, enabled = true, leadingIcon = {
            Icon("icons/search.svg", "SearchIcon", StandaloneSampleIcons::class.java, Modifier.size(16.dp))
        })
    }
}
