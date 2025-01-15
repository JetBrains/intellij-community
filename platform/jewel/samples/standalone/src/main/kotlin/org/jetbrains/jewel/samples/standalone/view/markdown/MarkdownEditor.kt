package org.jetbrains.jewel.samples.standalone.view.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.darkrockstudios.libraries.mpfilepicker.FilePicker
import com.darkrockstudios.libraries.mpfilepicker.JvmFile
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.ListComboBox
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.SimpleListItem
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextArea

@Composable
internal fun MarkdownEditor(state: TextFieldState, modifier: Modifier = Modifier) {
    Column(modifier) {
        ControlsRow(
            modifier = Modifier.fillMaxWidth().background(JewelTheme.globalColors.panelBackground).padding(8.dp),
            onLoadMarkdown = { state.edit { replace(0, length, it) } },
        )
        Divider(orientation = Orientation.Horizontal, Modifier.fillMaxWidth())
        Editor(state = state, modifier = Modifier.fillMaxWidth().weight(1f))
    }
}

@Composable
private fun ControlsRow(modifier: Modifier = Modifier, onLoadMarkdown: (String) -> Unit) {
    Row(
        modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        var showFilePicker by remember { mutableStateOf(false) }
        OutlinedButton(onClick = { showFilePicker = true }, modifier = Modifier.padding(start = 2.dp)) {
            Text("Load file...")
        }

        FilePicker(show = showFilePicker, fileExtensions = listOf("md")) { platformFile ->
            showFilePicker = false

            if (platformFile != null) {
                val jvmFile = platformFile as JvmFile
                val contents = jvmFile.platformFile.readText()

                onLoadMarkdown(contents)
            }
        }

        OutlinedButton(onClick = { onLoadMarkdown("") }) { Text("Clear") }

        Spacer(Modifier.weight(1f))

        val comboBoxItems = remember { listOf("Jewel readme", "Markdown catalog") }
        var selected by remember { mutableStateOf("Jewel readme") }
        ListComboBox(
            items = comboBoxItems,
            modifier = Modifier.width(170.dp).padding(end = 2.dp),
            maxPopupHeight = 150.dp,
            onSelectedItemChange = { _, text ->
                selected = text
                onLoadMarkdown(if (selected == "Jewel readme") JewelReadme else MarkdownCatalog)
            },
            itemContent = { item, isSelected, isActive ->
                SimpleListItem(text = item, isSelected = isSelected, isActive = isActive)
            },
        )
    }
}

@Composable
private fun Editor(state: TextFieldState, modifier: Modifier = Modifier) {
    Box(modifier) {
        TextArea(
            state = state,
            modifier = Modifier.align(Alignment.TopStart).fillMaxWidth(),
            undecorated = true,
            textStyle = JewelTheme.editorTextStyle,
            decorationBoxModifier = Modifier.padding(horizontal = 8.dp),
        )
    }
}
