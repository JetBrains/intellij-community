// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the
// Apache 2.0 license.
package org.jetbrains.jewel.markdown.scrolling

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.intellij.lang.annotations.Language
import org.jetbrains.jewel.markdown.LazyMarkdown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Scroll sync against a [LazyMarkdown] preview, which has no absolute scroll offset to map onto. */
public class LazyScrollingSynchronizerTest {
    @Test
    public fun `scrollToLine reaches a block that was never composed`() {
        doTest(manyParagraphs) { state, synchronizer ->
            assertEquals(0, state.firstVisibleItemIndex)

            // Far past the viewport, so the block has never been composed and has no position to look up
            synchronizer.scrollToLine(lineOfParagraph(40))
            assertEquals(40, state.firstVisibleItemIndex)
            assertEquals(0, state.firstVisibleItemScrollOffset)

            synchronizer.scrollToLine(lineOfParagraph(0))
            assertEquals(0, state.firstVisibleItemIndex)
            assertEquals(0, state.firstVisibleItemScrollOffset)
        }
    }

    @Test
    public fun `the editor and the preview map onto each other, ignoring items scrolled out of view`() {
        doTest(manyParagraphs) { state, synchronizer ->
            assertEquals(0, synchronizer.editorOffsetAtPreview(EDITOR_MAX_OFFSET, tenPixelsPerLine))

            // A paragraph's own editor offset puts it at the top of the viewport and maps back to itself. Going far
            // down and back disposes the items in between, whose last known positions must not be used.
            for (paragraph in listOf(5, 20, 1)) {
                val editorOffset = tenPixelsPerLine(lineOfParagraph(paragraph))
                synchronizer.scrollPreviewTo(editorOffset, EDITOR_MAX_OFFSET, tenPixelsPerLine)
                assertEquals(paragraph, state.firstVisibleItemIndex)
                assertEquals(0, state.firstVisibleItemScrollOffset)
                assertEquals(editorOffset, synchronizer.editorOffsetAtPreview(EDITOR_MAX_OFFSET, tenPixelsPerLine))
            }

            // Halfway between two paragraphs in the editor is partway into the earlier one in the preview
            val fifth = tenPixelsPerLine(lineOfParagraph(5))
            synchronizer.scrollPreviewTo(fifth, EDITOR_MAX_OFFSET, tenPixelsPerLine)
            synchronizer.scrollPreviewTo(
                (fifth + tenPixelsPerLine(lineOfParagraph(6))) / 2,
                EDITOR_MAX_OFFSET,
                tenPixelsPerLine,
            )
            assertEquals(5, state.firstVisibleItemIndex)
            assertTrue(state.firstVisibleItemScrollOffset > 0)
        }
    }

    @Test
    public fun `sync scrolling drives a lazy preview and the editor from each other`() {
        doTest(manyParagraphs) { state, synchronizer ->
            val editorScrollState = ScrollState(0)
            coroutineScope {
                val sync = launch { synchronizer.syncScrolling(editorScrollState) { tenPixelsPerLine } }
                settle()

                // The editor drives the preview...
                editorScrollState.scrollTo(tenPixelsPerLine(lineOfParagraph(7)))
                settle()
                assertEquals(7, state.firstVisibleItemIndex)
                assertEquals(0, state.firstVisibleItemScrollOffset)

                // ...and the preview drives the editor
                state.scrollToItem(3)
                settle()
                assertEquals(tenPixelsPerLine(lineOfParagraph(3)), editorScrollState.value)

                sync.cancel()
            }
        }
    }

    @Test
    public fun `scrolling within the only block advances the editor`() {
        doTest(oneTallCodeBlock) { state, synchronizer ->
            state.scrollToItem(0, VIEWPORT_SIZE * 2)
            assertTrue(state.firstVisibleItemScrollOffset > 0)

            assertTrue(synchronizer.editorOffsetAtPreview(oneBlockEditorMaxOffset, tenPixelsPerLine) > 0)
        }
    }

    @Test
    public fun `the editor scrolling within the only block advances the preview`() {
        doTest(oneTallCodeBlock) { state, synchronizer ->
            synchronizer.scrollPreviewTo(oneBlockEditorMaxOffset / 2, oneBlockEditorMaxOffset, tenPixelsPerLine)
            assertTrue(state.firstVisibleItemScrollOffset > 0)

            synchronizer.scrollPreviewTo(oneBlockEditorMaxOffset, oneBlockEditorMaxOffset, tenPixelsPerLine)
            assertFalse("the editor is at its end, so the preview must be too", state.canScrollForward)
        }
    }

    @Test
    public fun `both panes reach their ends together`() {
        doTest(manyParagraphs) { state, synchronizer ->
            synchronizer.scrollPreviewTo(tailEditorMaxOffset, tailEditorMaxOffset, tenPixelsPerLine)
            assertFalse("the editor is at its end, so the preview must be too", state.canScrollForward)

            state.scrollToItem(PARAGRAPH_COUNT - 1)
            assertFalse("scrolling to the last item leaves the list at its end", state.canScrollForward)
            assertEquals(tailEditorMaxOffset, synchronizer.editorOffsetAtPreview(tailEditorMaxOffset, tenPixelsPerLine))
        }
    }

    @Test
    public fun `the last stretch keeps moving while the end of the list is out of sight`() {
        doTest(tallCodeBlockThenTail) { state, synchronizer ->
            // The editor runs out of scroll inside the code block, so the paragraph after it never reaches the top,
            // and the block is taller than the viewport, so neither does the end of the list
            synchronizer.scrollPreviewTo(croppedEditorMaxOffset / 2, croppedEditorMaxOffset, tenPixelsPerLine)
            assertEquals(0, state.firstVisibleItemIndex)
            val halfway = state.firstVisibleItemScrollOffset
            assertTrue(halfway > 0)
            assertTrue(synchronizer.editorOffsetAtPreview(croppedEditorMaxOffset, tenPixelsPerLine) > 0)

            synchronizer.scrollPreviewTo(croppedEditorMaxOffset * 3 / 4, croppedEditorMaxOffset, tenPixelsPerLine)
            assertTrue(state.firstVisibleItemScrollOffset > halfway)
        }
    }

    /** Each paragraph is one source line, with a blank line between them, so paragraph `n` starts on line `2n`. */
    private fun lineOfParagraph(index: Int) = index * 2

    @Language("Markdown") private val manyParagraphs = (0..<PARAGRAPH_COUNT).joinToString("\n\n") { "p$it" }

    /** An editor that stops scrolling five paragraphs before the end, the way its own last screenful would. */
    private val tailEditorMaxOffset = tenPixelsPerLine(lineOfParagraph(PARAGRAPH_COUNT - 5))

    /** A fenced code block taller than the viewport, and the whole document: one lazy item to scroll within. */
    private val oneTallCodeBlock = "```text\n" + (1..CODE_BLOCK_LINES).joinToString("\n") { "line $it" } + "\n```"

    /** The closing fence is the last source line, and the editor can't scroll it past its own viewport. */
    private val oneBlockEditorMaxOffset = tenPixelsPerLine(CODE_BLOCK_LINES + 1) - VIEWPORT_SIZE

    /** The same block, no longer the last one: a preview that renders a tail the editor has no room left for. */
    private val tallCodeBlockThenTail = "$oneTallCodeBlock\n\ntail paragraph"

    /** An editor out of scroll halfway through the code block, well before the paragraph that follows it. */
    private val croppedEditorMaxOffset = tenPixelsPerLine(CODE_BLOCK_LINES / 2)

    private fun doTest(markdown: String, action: suspend (LazyListState, ContinuousScrollingSynchronizer) -> Unit) {
        val state = LazyListState()
        runScrollSyncTest(
            scrollState = state,
            // A viewport small enough that most of the document is never composed
            content = { processor ->
                Box(Modifier.size(VIEWPORT_SIZE.dp)) {
                    LazyMarkdown(blocks = processor.processMarkdownDocument(markdown), state = state)
                }
            },
            action = action,
        )
    }

    private companion object {
        const val PARAGRAPH_COUNT = 50
        const val VIEWPORT_SIZE = 100
        const val EDITOR_MAX_OFFSET = 10_000
        const val CODE_BLOCK_LINES = 100
    }
}
