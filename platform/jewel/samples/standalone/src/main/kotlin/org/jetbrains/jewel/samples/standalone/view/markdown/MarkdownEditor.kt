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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.darkrockstudios.libraries.mpfilepicker.FilePicker
import com.darkrockstudios.libraries.mpfilepicker.JvmFile
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.samples.standalone.StandaloneSampleIcons
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.PopupMenu
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextArea

@Composable
internal fun MarkdownEditor(
    currentMarkdown: String,
    onMarkdownChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        ControlsRow(onMarkdownChange, Modifier.fillMaxWidth().background(JewelTheme.globalColors.paneBackground).padding(8.dp))
        Divider(orientation = Orientation.Horizontal)
        Editor(currentMarkdown, onMarkdownChange, Modifier.fillMaxWidth().weight(1f))
    }
}

@Composable
private fun ControlsRow(onMarkdownChange: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        var showFilePicker by remember { mutableStateOf(false) }
        OutlinedButton(
            onClick = { showFilePicker = true },
            modifier = Modifier.padding(start = 2.dp),
        ) {
            Text("Load file...")
        }

        FilePicker(show = showFilePicker, fileExtensions = listOf("md")) { platformFile ->
            showFilePicker = false

            if (platformFile != null) {
                val jvmFile = platformFile as JvmFile
                val contents = jvmFile.platformFile.readText()

                onMarkdownChange(contents)
            }
        }

        OutlinedButton(onClick = { onMarkdownChange("") }) { Text("Clear") }

        Box {
            var showPresets by remember { mutableStateOf(false) }
            OutlinedButton(onClick = { showPresets = true }) {
                Text("Load preset")
                Spacer(Modifier.width(8.dp))
                Icon(
                    resource = "expui/general/chevronDown.svg",
                    contentDescription = null,
                    iconClass = StandaloneSampleIcons::class.java,
                )
            }

            if (showPresets) {
                var selected by remember { mutableStateOf("Jewel readme") }
                PopupMenu(onDismissRequest = {
                    showPresets = false
                    false
                }, horizontalAlignment = Alignment.Start) {
                    selectableItem(
                        selected = selected == "Jewel readme",
                        onClick = {
                            selected = "Jewel readme"
                            onMarkdownChange(JewelReadme)
                        },
                    ) {
                        Text("Jewel readme")
                    }

                    selectableItem(
                        selected = selected == "Markdown catalog",
                        onClick = {
                            selected = "Markdown catalog"
                            onMarkdownChange(MarkdownCatalog)
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
private fun Editor(
    currentMarkdown: String,
    onMarkdownChange: (String) -> Unit,
    modifier: Modifier,
) {
    val monospacedTextStyle = JewelTheme.defaultTextStyle.copy(fontFamily = FontFamily.Monospace)

    TextArea(
        value = currentMarkdown,
        onValueChange = onMarkdownChange,
        modifier = modifier.padding(16.dp),
        undecorated = true,
        textStyle = monospacedTextStyle,
    )
}
