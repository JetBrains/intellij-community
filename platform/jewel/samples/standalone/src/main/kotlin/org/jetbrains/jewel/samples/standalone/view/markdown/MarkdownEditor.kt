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
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.PopupMenu
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextArea
import org.jetbrains.jewel.ui.icons.AllIconsKeys

@Composable
internal fun MarkdownEditor(state: TextFieldState, modifier: Modifier = Modifier) {
    Column(modifier) {
        ControlsRow(
            modifier = Modifier.fillMaxWidth().background(JewelTheme.globalColors.panelBackground).padding(8.dp),
            onLoadMarkdown = { state.edit { replace(0, length, it) } },
        )
        Divider(orientation = Orientation.Horizontal)
        Editor(state = state, modifier = Modifier.fillMaxWidth().weight(1f))
    }
}

@Composable
private fun ControlsRow(modifier: Modifier = Modifier, onLoadMarkdown: (String) -> Unit) {
    Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
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

        Box {
            var showPresets by remember { mutableStateOf(false) }
            OutlinedButton(onClick = { showPresets = true }) {
                Text("Load preset")
                Spacer(Modifier.width(8.dp))
                Icon(AllIconsKeys.General.ChevronDown, contentDescription = null)
            }

            if (showPresets) {
                var selected by remember { mutableStateOf("Jewel readme") }
                PopupMenu(
                    onDismissRequest = {
                        showPresets = false
                        false
                    },
                    horizontalAlignment = Alignment.Start,
                ) {
                    selectableItem(
                        selected = selected == "Jewel readme",
                        onClick = {
                            selected = "Jewel readme"
                            onLoadMarkdown(JewelReadme)
                        },
                    ) {
                        Text("Jewel readme")
                    }

                    selectableItem(
                        selected = selected == "Markdown catalog",
                        onClick = {
                            selected = "Markdown catalog"
                            onLoadMarkdown(MarkdownCatalog)
                        },
                    ) {
                        Text("Markdown catalog")
                    }
                }
            }
        }
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
