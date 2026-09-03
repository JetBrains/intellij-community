// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the
// Apache 2.0 license.
package org.jetbrains.jewel.markdown.scrolling

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import org.jetbrains.jewel.markdown.Markdown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The editor side of scroll sync: how a real Compose text layout's lines map onto the offsets the synchronizer anchors
 * blocks against, first on its own through [sourceLineOffsets] and then end to end through the synchronizer.
 *
 * [ScrollingSynchronizerTest] and [LazyScrollingSynchronizerTest] cover the preview side, and both feed the
 * synchronizer [tenPixelsPerLine], a stub that assumes the parser and the editor agree on what a line is and that no
 * line ever wraps. Those two assumptions are what this file exists to check.
 */
public class EditorScrollSyncTest {
    @Test
    public fun `a line has an offset, and a line past the end has none`() {
        val offsets = offsetsOf("\n")
        assertEquals(
            "one offset per line, none past the end, was $offsets",
            PARAGRAPH_COUNT,
            offsets.count { it != null },
        )
        assertTrue(
            "lines must move down the layout, was $offsets",
            offsets.filterNotNull().zipWithNext().all { (a, b) -> b > a },
        )
    }

    @Test
    public fun `CRLF numbers lines the same way as LF`() {
        assertEquals(offsetsOf("\n"), offsetsOf("\r\n"))
    }

    @Test
    public fun `a lone CR still counts as a line, though the editor lays the document out as one`() {
        // Compose doesn't break a line on a lone CR, so the whole document is laid out as a single line and every
        // source line really does sit at its top. commonmark does count them, so each must report that offset rather
        // than report itself as a line the editor doesn't have, which would drop its block from the sync entirely.
        val offsets = offsetsOf("\r")
        assertEquals(
            "every line commonmark counts needs an offset, was $offsets",
            PARAGRAPH_COUNT,
            offsets.count { it != null },
        )
        assertEquals(List(PARAGRAPH_COUNT) { 0 }, offsets.take(PARAGRAPH_COUNT))
    }

    @Test
    public fun `a block's parser line is the line the editor measured`() {
        // If the editor numbered lines differently from commonmark -- by not counting CRLF as one terminator, say --
        // each block's anchor would take the offset of some other line, and the two paths would disagree here.
        doTest(paragraphs(separator = "\r\n"), width = WIDE) { scrollState, synchronizer, editor ->
            val reachable = reachableParagraphs(editor)
            assertTrue("no paragraph can reach the top of this editor, so nothing is asserted", reachable.isNotEmpty())

            for (paragraph in reachable) {
                synchronizer.scrollToLine(lineOfParagraph(paragraph))
                val byBlock = scrollState.value

                synchronizer.scrollPreviewTo(editorOffsetOf(editor, paragraph), editor.maxOffset, editor.offsetOfLine)
                assertEquals("paragraph $paragraph", byBlock, scrollState.value)
            }
        }
    }

    @Test
    public fun `CRLF and LF documents sync identically`() {
        val lf = mutableListOf<Int>()
        doTest(paragraphs(separator = "\n"), width = WIDE) { scrollState, synchronizer, editor ->
            lf += probe(scrollState, synchronizer, editor)
        }

        val crlf = mutableListOf<Int>()
        doTest(paragraphs(separator = "\r\n"), width = WIDE) { scrollState, synchronizer, editor ->
            crlf += probe(scrollState, synchronizer, editor)
        }

        assertTrue("the preview never moved, so this would pass either way", lf.any { it > 0 })
        assertEquals(lf, crlf)
    }

    @Test
    public fun `the preview keeps moving while the editor crosses a soft-wrapped line`() {
        // The jump continuous sync exists to remove: scrollToLine holds the preview still for the whole wrapped line
        // and then moves it all at once when the next block's line is reached.
        doTest(wrappedDocument, width = NARROW) { scrollState, synchronizer, editor ->
            val wrappedLine = lineOfParagraph(1)
            assertTrue("the long paragraph must soft-wrap at this width", editor.wraps(wrappedLine))

            val top = editor.offsetOf(wrappedLine)
            val nextTop = editor.offsetOf(lineOfParagraph(2))
            assertTrue("the editor must be able to scroll past the wrapped line", nextTop in 1..<editor.maxOffset)

            synchronizer.scrollPreviewTo(top, editor.maxOffset, editor.offsetOfLine)
            val atTop = scrollState.value
            synchronizer.scrollPreviewTo((top + nextTop) / 2, editor.maxOffset, editor.offsetOfLine)
            val halfway = scrollState.value
            synchronizer.scrollPreviewTo(nextTop, editor.maxOffset, editor.offsetOfLine)
            val atNext = scrollState.value

            assertTrue("the preview must move partway through the wrap, was $atTop then $halfway", halfway > atTop)
            assertTrue("and must not reach the next block early, was $halfway then $atNext", halfway < atNext)
        }
    }

    /** The offset of each line of a document joined by [terminator], plus one line past its end. */
    private fun offsetsOf(terminator: String): List<Int?> {
        val editor = measureEditor((0..<PARAGRAPH_COUNT).joinToString(terminator) { "line $it" })
        return List(PARAGRAPH_COUNT + 1) { editor.offsetOfLine(it) }
    }

    /** Where the preview lands for each reachable paragraph's own editor offset, as a signature of the mapping. */
    private suspend fun probe(
        scrollState: ScrollState,
        synchronizer: ContinuousScrollingSynchronizer,
        editor: MeasuredEditor,
    ) = buildList {
        for (paragraph in reachableParagraphs(editor)) {
            synchronizer.scrollPreviewTo(editorOffsetOf(editor, paragraph), editor.maxOffset, editor.offsetOfLine)
            add(scrollState.value)
        }
    }

    /**
     * The paragraphs whose first line the editor can actually bring to its top. A line past the editor's own maximum
     * scroll offset never anchors anything, so asserting about it would only be asserting about the clamp.
     */
    private fun reachableParagraphs(editor: MeasuredEditor) =
        (0..<PARAGRAPH_COUNT).filter { editorOffsetOf(editor, it) in 1..<editor.maxOffset }

    private fun editorOffsetOf(editor: MeasuredEditor, paragraph: Int) = editor.offsetOf(lineOfParagraph(paragraph))

    /** Paragraph `n` starts on line `2n`, with a blank line between paragraphs. */
    private fun lineOfParagraph(index: Int) = index * 2

    private fun paragraphs(separator: String) =
        (0..<PARAGRAPH_COUNT).joinToString(separator + separator) { "paragraph $it" }

    /** One paragraph long enough to wrap many times at [NARROW], with enough after it to be scrolled past. */
    private val wrappedDocument =
        (listOf("first", (1..30).joinToString(" ") { "word$it" }) + (1..20).map { "tail $it" }).joinToString("\n\n")

    private fun doTest(
        markdown: String,
        width: Dp,
        action: suspend (ScrollState, ContinuousScrollingSynchronizer, MeasuredEditor) -> Unit,
    ) {
        var editor: MeasuredEditor? = null
        runScrollSyncTest(
            scrollState = ScrollState(0),
            content = { processor ->
                editor = rememberMeasuredEditor(markdown, width)
                Box(Modifier.size(width, MEASURED_EDITOR_VIEWPORT)) {
                    Markdown(markdownBlocks = processor.processMarkdownDocument(markdown), markdown = "")
                }
            },
            action = { scrollState, synchronizer ->
                action(scrollState, synchronizer, checkNotNull(editor) { "the editor was never measured" })
            },
        )
    }

    private companion object {
        const val PARAGRAPH_COUNT = 24
        val WIDE = 400.dp
        val NARROW = 60.dp
    }
}

/**
 * A source document laid out the way an editor lays it out, rather than assumed to be one fixed-height visual line per
 * source line the way [tenPixelsPerLine] is.
 *
 * `offsetOfLine` is [sourceLineOffsets] on the real layout, and `maxOffset` how far this editor could scroll in a
 * [MEASURED_EDITOR_VIEWPORT]-tall viewport.
 */
private class MeasuredEditor(val offsetOfLine: (Int) -> Int?, val maxOffset: Int, private val lineHeight: Int) {
    /** The offset of [line], failing rather than returning null: a test asking for a line expects it to exist. */
    fun offsetOf(line: Int): Int = checkNotNull(offsetOfLine(line)) { "the editor has no line $line" }

    /** Whether [line] takes more than one visual line, i.e. the layout soft-wrapped it. */
    fun wraps(line: Int): Boolean = offsetOf(line + 1) - offsetOf(line) > lineHeight
}

/**
 * Measures [text] as an editor [width] wide would lay it out, soft wrapping included. Called from a test's `content` so
 * it is measured in the same composition, with the same density, as the preview it will be synced against.
 */
@Composable
private fun rememberMeasuredEditor(text: String, width: Dp): MeasuredEditor {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(text, width, measurer, density) {
        val constraints =
            if (width == Dp.Unspecified) Constraints() else Constraints(maxWidth = with(density) { width.roundToPx() })
        val layout = measurer.measure(AnnotatedString(text), MEASURED_EDITOR_STYLE, constraints = constraints)
        val viewportPx = with(density) { MEASURED_EDITOR_VIEWPORT.roundToPx() }
        MeasuredEditor(
            offsetOfLine = layout.sourceLineOffsets(),
            maxOffset = (layout.size.height - viewportPx).coerceAtLeast(0),
            lineHeight = (layout.getLineBottom(0) - layout.getLineTop(0)).roundToInt(),
        )
    }
}

/** Measures [text] on its own, unwrapped, for checks that only need the editor side and no preview to sync against. */
@OptIn(ExperimentalTestApi::class)
private fun measureEditor(text: String): MeasuredEditor {
    var editor: MeasuredEditor? = null
    runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                editor = rememberMeasuredEditor(text, Dp.Unspecified)
            }
        }
        waitForIdle()
    }
    return checkNotNull(editor) { "the text was never laid out, so nothing was asserted" }
}

/** The viewport [MeasuredEditor.maxOffset] is measured against, standing in for the editor pane's height. */
private val MEASURED_EDITOR_VIEWPORT: Dp = 100.dp

private val MEASURED_EDITOR_STYLE = TextStyle.Default.copy(fontSize = 10.sp)
