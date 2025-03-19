package org.jetbrains.jewel.samples.standalone.view

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
            val editorState = rememberTextFieldState(JewelReadme)
            MarkdownEditor(state = editorState, modifier = Modifier.fillMaxHeight().weight(1f))

            Divider(Orientation.Vertical, Modifier.fillMaxHeight())

            MarkdownPreview(modifier = Modifier.fillMaxHeight().weight(1f), rawMarkdown = editorState.text)
        }
    }
}
