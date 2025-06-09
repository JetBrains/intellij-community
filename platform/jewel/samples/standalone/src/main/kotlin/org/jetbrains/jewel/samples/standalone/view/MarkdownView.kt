package org.jetbrains.jewel.samples.standalone.view

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import org.jetbrains.jewel.foundation.modifier.trackActivation
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.markdown.MarkdownMode
import org.jetbrains.jewel.markdown.WithMarkdownMode
import org.jetbrains.jewel.samples.standalone.markdown.JewelReadme
import org.jetbrains.jewel.samples.standalone.markdown.MarkdownEditor
import org.jetbrains.jewel.samples.standalone.markdown.MarkdownPreview
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.Divider

@Composable
internal fun MarkdownDemo() {
    Row(Modifier.trackActivation().fillMaxSize().background(JewelTheme.globalColors.panelBackground)) {
        WithMarkdownMode(MarkdownMode.EditorPreview(scrollingSynchronizer = null)) {
            var editorState by remember { mutableStateOf(TextFieldValue(JewelReadme)) }
            MarkdownEditor(state = editorState, modifier = Modifier.fillMaxHeight().weight(1f)) { editorState = it }

            Divider(Orientation.Vertical, Modifier.fillMaxHeight())

            MarkdownPreview(modifier = Modifier.fillMaxHeight().weight(1f), rawMarkdown = editorState.text)
        }
    }
}
