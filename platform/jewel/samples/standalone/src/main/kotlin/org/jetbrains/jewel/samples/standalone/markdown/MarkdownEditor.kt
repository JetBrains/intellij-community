package org.jetbrains.jewel.samples.standalone.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.awt.Component
import java.awt.Frame
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import org.jetbrains.jewel.foundation.LocalComponent
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.ListComboBox
import org.jetbrains.jewel.ui.component.OutlinedButton
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
private fun ControlsRow(onLoadMarkdown: (String) -> Unit, modifier: Modifier = Modifier) {
    val component = LocalComponent.current
    Row(modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(
            onClick = {
                val file = pickMarkdownFile(component)
                if (file != null) {
                    onLoadMarkdown(file.readText())
                }
            },
            modifier = Modifier.padding(start = 2.dp),
        ) {
            Text("Load file...")
        }

        Spacer(Modifier.width(10.dp))

        OutlinedButton(onClick = { onLoadMarkdown("") }) { Text("Clear") }

        Spacer(Modifier.weight(1f))

        val comboBoxItems = remember { listOf("Jewel readme", "Markdown catalog") }
        var selectedIndex by remember { mutableIntStateOf(0) }
        ListComboBox(
            items = comboBoxItems,
            selectedIndex = selectedIndex,
            onSelectedItemChange = { index ->
                selectedIndex = index
                onLoadMarkdown(if (selectedIndex == 0) JewelReadme else MarkdownCatalog)
            },
            modifier = Modifier.width(170.dp).padding(end = 2.dp),
            maxPopupHeight = 150.dp,
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

private fun pickMarkdownFile(component: Component): File? {
    val fileChooser =
        JFileChooser().apply {
            @Suppress("HardCodedStringLiteral")
            dialogTitle = "Select a Markdown file"
            fileSelectionMode = JFileChooser.FILES_ONLY
            isMultiSelectionEnabled = false
            fileFilter = FileNameExtensionFilter("Markdown Files", "md")
            isAcceptAllFileFilterUsed = false
        }

    return if (fileChooser.showOpenDialog(component) == JFileChooser.APPROVE_OPTION) {
        fileChooser.selectedFile.takeIf { it.extension == "md" }
    } else {
        null
    }
}

private val Component.frame: Frame
    get() = this as? Frame ?: parent.frame
