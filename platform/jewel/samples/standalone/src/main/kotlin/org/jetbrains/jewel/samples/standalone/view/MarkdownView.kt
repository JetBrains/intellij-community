package org.jetbrains.jewel.samples.standalone.view

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import org.jetbrains.jewel.foundation.modifier.trackActivation
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.markdown.MarkdownMode
import org.jetbrains.jewel.markdown.WithMarkdownMode
import org.jetbrains.jewel.markdown.scrolling.ScrollingSynchronizer
import org.jetbrains.jewel.markdown.scrolling.sourceLineOffsets
import org.jetbrains.jewel.markdown.scrolling.syncScrolling
import org.jetbrains.jewel.samples.standalone.markdown.JewelReadme
import org.jetbrains.jewel.samples.standalone.markdown.MarkdownEditor
import org.jetbrains.jewel.samples.standalone.markdown.MarkdownPreview
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.Divider

@Composable
internal fun MarkdownDemo() {
    val editorScrollState = rememberScrollState()
    val previewScrollState = rememberScrollState()
    val synchronizer = remember(previewScrollState) { ScrollingSynchronizer.create(previewScrollState) }
    val markdownMode = remember(synchronizer) { MarkdownMode.EditorPreview(synchronizer) }

    var editorLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val editorOffsetOfLine by remember { derivedStateOf { editorLayout?.sourceLineOffsets() ?: { _: Int -> null } } }
    LaunchedEffect(synchronizer) {
        synchronizer?.syncScrolling(editorScrollState, previewScrollState) { editorOffsetOfLine }
    }

    Row(
        Modifier.trackActivation().fillMaxSize().background(JewelTheme.globalColors.panelBackground).semantics {
            isTraversalGroup = true
        }
    ) {
        WithMarkdownMode(markdownMode) {
            val editorState = rememberTextFieldState(JewelReadme)
            MarkdownEditor(
                state = editorState,
                scrollState = editorScrollState,
                onTextLayout = { editorLayout = it },
                modifier = Modifier.fillMaxHeight().weight(1f),
            )

            Divider(Orientation.Vertical, Modifier.fillMaxHeight())

            MarkdownPreview(
                rawMarkdown = editorState.text,
                scrollState = previewScrollState,
                modifier = Modifier.fillMaxHeight().weight(1f),
            )
        }
    }
}
