// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the
// Apache 2.0 license.
package org.jetbrains.jewel.markdown.scrolling

import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.intellij.lang.annotations.Language
import org.jetbrains.jewel.markdown.Markdown
import org.jetbrains.jewel.markdown.MarkdownBlock
import org.jetbrains.jewel.markdown.processing.MarkdownProcessor
import org.jetbrains.jewel.markdown.testing.createMarkdownTestStyling
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@Suppress("LargeClass")
public class ScrollingSynchronizerTest {
    @Test
    public fun headings() {
        @Language("Markdown")
        val markdown =
            """
            |# Heading 1
            |## Heading 2
            |### Heading 3
            """
                .trimMargin()
        doTest(markdown) { scrollState, synchronizer ->
            synchronizer.scrollToLine(0)
            assertEquals(0, scrollState.value)

            synchronizer.scrollToLine(1)
            val h2Top = scrollState.value
            assertTrue(h2Top > 0)

            synchronizer.scrollToLine(2)
            val h3Top = scrollState.value
            assertTrue(h3Top > h2Top)

            synchronizer.scrollToLine(1)
            assertEquals(h2Top, scrollState.value)

            synchronizer.scrollToLine(0)
            synchronizer.scrollToLine(2)
            assertEquals(h3Top, scrollState.value)
        }
    }

    @Test
    public fun paragraphs() {
        doTest(threeParagraphs) { scrollState, synchronizer ->
            synchronizer.scrollToLine(1)
            val p2Top = scrollState.value
            assertTrue(p2Top > 0)

            synchronizer.scrollToLine(2)
            assertEquals(p2Top, scrollState.value)

            synchronizer.scrollToLine(3)
            val p3Top = scrollState.value
            assertTrue(p3Top > p2Top)

            synchronizer.scrollToLine(4)
            assertEquals(p3Top, scrollState.value)

            synchronizer.scrollToLine(1)
            assertEquals(p2Top, scrollState.value)
        }
    }

    @Test
    public fun `empty spaces`() {
        @Language("Markdown")
        val markdown =
            """
            |# Heading 1
            |
            |
            |# Heading 2
            |
            |
            |## Heading 3
            |
            |
            """
                .trimMargin()
        doTest(markdown) { scrollState, synchronizer ->
            synchronizer.scrollToLine(0)
            assertEquals(0, scrollState.value)

            synchronizer.scrollToLine(1)
            val h2Top = scrollState.value
            assertTrue(h2Top > 0)

            synchronizer.scrollToLine(2)
            assertEquals(h2Top, scrollState.value)

            synchronizer.scrollToLine(3)
            assertEquals(h2Top, scrollState.value)

            synchronizer.scrollToLine(4)
            val h3Top = scrollState.value
            assertTrue(h3Top > h2Top)

            synchronizer.scrollToLine(5)
            assertEquals(h3Top, scrollState.value)

            synchronizer.scrollToLine(6)
            assertEquals(h3Top, scrollState.value)

            synchronizer.scrollToLine(7)
            assertEquals(h3Top, scrollState.value)

            synchronizer.scrollToLine(8)
            assertEquals(h3Top, scrollState.value)

            synchronizer.scrollToLine(1)
            assertEquals(h2Top, scrollState.value)
        }
    }

    @Test
    public fun `unordered list`() {
        @Language("Markdown")
        val markdown =
            """
            |Items:
            |- item 1
            |    - subitem A
            |- item 2
            |- item 3
            """
                .trimMargin()
        doTest(markdown) { scrollState, synchronizer ->
            synchronizer.scrollToLine(1)
            val i1Top = scrollState.value
            assertTrue(i1Top > 0)

            synchronizer.scrollToLine(2)
            val siATop = scrollState.value
            assertTrue(siATop > i1Top)

            synchronizer.scrollToLine(3)
            val i2Top = scrollState.value
            assertTrue(i2Top > siATop)

            synchronizer.scrollToLine(4)
            val i3Top = scrollState.value
            assertTrue(i3Top > i2Top)

            synchronizer.scrollToLine(2)
            assertEquals(siATop, scrollState.value)
        }
    }

    @Test
    public fun `ordered list`() {
        @Language("Markdown")
        val markdown =
            """
            |Items:
            |1. item 1
            |    1. subitem A
            |2. item 2
            |3. item 3
            """
                .trimMargin()
        doTest(markdown) { scrollState, synchronizer ->
            synchronizer.scrollToLine(1)
            val i1Top = scrollState.value
            assertTrue(i1Top > 0)

            synchronizer.scrollToLine(2)
            val siATop = scrollState.value
            assertTrue(siATop > i1Top)

            synchronizer.scrollToLine(3)
            val i2Top = scrollState.value
            assertTrue(i2Top > siATop)

            synchronizer.scrollToLine(4)
            val i3Top = scrollState.value
            assertTrue(i3Top > i2Top)

            synchronizer.scrollToLine(2)
            assertEquals(siATop, scrollState.value)
        }
    }

    @Test
    public fun `fenced code block`() {
        @Language("Markdown")
        val markdown =
            """
            |```kotlin
            |package my.awesome.pkg
            |
            |fun main() {
            |    println("Hello world")
            |}
            |```
            """
                .trimMargin()
        doTest(markdown) { scrollState, synchronizer ->
            synchronizer.scrollToLine(1)
            val packageTop = scrollState.value
            assertTrue(packageTop > 0)

            synchronizer.scrollToLine(2)
            val emptyLineTop = scrollState.value
            assertTrue(emptyLineTop > packageTop)

            synchronizer.scrollToLine(3)
            val mainTop = scrollState.value
            assertTrue(mainTop > emptyLineTop)

            synchronizer.scrollToLine(4)
            val printlnTop = scrollState.value
            assertTrue(printlnTop > mainTop)

            synchronizer.scrollToLine(5)
            val rBracketTop = scrollState.value
            assertTrue(rBracketTop > printlnTop)

            synchronizer.scrollToLine(2)
            assertEquals(emptyLineTop, scrollState.value)

            assertSameDistance(
                distance = CODE_TEXT_SIZE + 2,
                packageTop,
                emptyLineTop,
                mainTop,
                printlnTop,
                rBracketTop,
            )
        }
    }

    @Test
    public fun `indented code block`() {
        @Language("Markdown")
        val markdown =
            """
            |Here starts the indented code block.
            |
            |    package my.awesome.pkg
            |
            |    fun main() {
            |        println("Hello world")
            |    }
            """
                .trimMargin()
        doTest(markdown) { scrollState, synchronizer ->
            synchronizer.scrollToLine(2)
            val packageTop = scrollState.value
            assertTrue(packageTop > 0)

            synchronizer.scrollToLine(3)
            val emptyLineTop = scrollState.value
            assertTrue(emptyLineTop > packageTop)

            synchronizer.scrollToLine(4)
            val mainTop = scrollState.value
            assertTrue(mainTop > emptyLineTop)

            synchronizer.scrollToLine(5)
            val printlnTop = scrollState.value
            assertTrue(printlnTop > mainTop)

            synchronizer.scrollToLine(6)
            val rBracketTop = scrollState.value
            assertTrue(rBracketTop > printlnTop)

            synchronizer.scrollToLine(3)
            assertEquals(emptyLineTop, scrollState.value)

            assertSameDistance(
                distance = CODE_TEXT_SIZE + 2,
                packageTop,
                emptyLineTop,
                mainTop,
                printlnTop,
                rBracketTop,
            )
        }
    }

    @Test
    public fun `HTML list`() {
        @Language("Markdown")
        val markdown =
            """
            |Items:
            |<ul>
            |  <li>item 1
            |    <ul>
            |      <li>subitem A</li>
            |    </ul>
            |  </li>
            |  <li>item 2</li>
            |  <li>item 3</li>
            |</ul>
            """
                .trimMargin()
        doTest(markdown) { scrollState, synchronizer ->
            synchronizer.scrollToLine(2)
            val i1Top = scrollState.value
            assertTrue(i1Top >= 0)

            synchronizer.scrollToLine(4)
            val siATop = scrollState.value
            assertTrue(siATop >= i1Top)

            synchronizer.scrollToLine(7)
            val i2Top = scrollState.value
            assertTrue(i2Top >= siATop)

            synchronizer.scrollToLine(8)
            val i3Top = scrollState.value
            assertTrue(i3Top >= i2Top)

            synchronizer.scrollToLine(4)
            assertEquals(siATop, scrollState.value)
        }
    }

    @Test
    public fun `HTML code block`() {
        @Language("Markdown")
        val markdown =
            """
            |<pre>
            |package my.awesome.pkg
            |
            |fun main() {
            |    println("Hello world")
            |}
            |</pre>
            """
                .trimMargin()
        doTest(markdown) { scrollState, synchronizer ->
            synchronizer.scrollToLine(1)
            val packageTop = scrollState.value
            assertTrue(packageTop >= 0)

            synchronizer.scrollToLine(2)
            val emptyLineTop = scrollState.value
            assertTrue(emptyLineTop > packageTop)

            synchronizer.scrollToLine(3)
            val mainTop = scrollState.value
            assertTrue(mainTop > emptyLineTop)

            synchronizer.scrollToLine(4)
            val printlnTop = scrollState.value
            assertTrue(printlnTop > mainTop)

            synchronizer.scrollToLine(5)
            val rBracketTop = scrollState.value
            assertTrue(rBracketTop > printlnTop)

            synchronizer.scrollToLine(2)
            assertEquals(emptyLineTop, scrollState.value)

            assertSameDistance(
                distance = CODE_TEXT_SIZE + 2,
                packageTop,
                emptyLineTop,
                mainTop,
                printlnTop,
                rBracketTop,
            )
        }
    }

    @Test
    public fun `add a block`() {
        @Language("Markdown")
        val firstRun =
            """
            |```kotlin
            |package my.awesome.pkg
            |
            |fun main() {
            |    println("Hello world")
            |}
            |```
            """
                .trimMargin()

        @Language("Markdown")
        val secondRun =
            """
            |**CHANGE**
            |
            |```kotlin
            |package my.awesome.pkg
            |
            |fun main() {
            |    println("Hello world")
            |}
            |```
            """
                .trimMargin()

        doTest(firstRun, secondRun) { scrollState, synchronizer ->
            synchronizer.scrollToLine(3)
            val packageTop = scrollState.value
            assertTrue(packageTop > 0)

            synchronizer.scrollToLine(4)
            val emptyLineTop = scrollState.value
            assertTrue(emptyLineTop > packageTop)

            synchronizer.scrollToLine(5)
            val mainTop = scrollState.value
            assertTrue(mainTop > emptyLineTop)

            synchronizer.scrollToLine(6)
            val printlnTop = scrollState.value
            assertTrue(printlnTop > mainTop)

            synchronizer.scrollToLine(7)
            val rBracketTop = scrollState.value
            assertTrue(rBracketTop > printlnTop)

            synchronizer.scrollToLine(4)
            assertEquals(emptyLineTop, scrollState.value)

            assertSameDistance(
                distance = CODE_TEXT_SIZE + 2,
                packageTop,
                emptyLineTop,
                mainTop,
                printlnTop,
                rBracketTop,
            )
        }
    }

    @Test
    public fun `remove a block`() {
        @Language("Markdown")
        val firstRun =
            """
            |**CHANGE**
            |
            |```kotlin
            |package my.awesome.pkg
            |
            |fun main() {
            |    println("Hello world")
            |}
            |```
            """
                .trimMargin()

        @Language("Markdown")
        val secondRun =
            """
            |```kotlin
            |package my.awesome.pkg
            |
            |fun main() {
            |    println("Hello world")
            |}
            |```
            """
                .trimMargin()

        doTest(firstRun, secondRun) { scrollState, synchronizer ->
            synchronizer.scrollToLine(1)
            val packageTop = scrollState.value
            assertTrue(packageTop > 0)

            synchronizer.scrollToLine(2)
            val emptyLineTop = scrollState.value
            assertTrue(emptyLineTop > packageTop)

            synchronizer.scrollToLine(3)
            val mainTop = scrollState.value
            assertTrue(mainTop > emptyLineTop)

            synchronizer.scrollToLine(4)
            val printlnTop = scrollState.value
            assertTrue(printlnTop > mainTop)

            synchronizer.scrollToLine(5)
            val rBracketTop = scrollState.value
            assertTrue(rBracketTop > printlnTop)

            synchronizer.scrollToLine(2)
            assertEquals(emptyLineTop, scrollState.value)

            assertSameDistance(
                distance = CODE_TEXT_SIZE + 2,
                packageTop,
                emptyLineTop,
                mainTop,
                printlnTop,
                rBracketTop,
            )
        }
    }

    @Test
    public fun `change a block`() {
        @Language("Markdown")
        val firstRun =
            """
            |```kotlin
            |package my.awesome.pkg
            |
            |fun main() {
            |    println("Hello world")
            |}
            |```
            """
                .trimMargin()

        @Language("Markdown")
        val secondRun =
            """
            |```kotlin
            |package my.awesome.pkg
            |
            |fun main() {
            |    val name = "Steve"
            |    println("Hello " + name)
            |}
            |```
            """
                .trimMargin()

        doTest(firstRun, secondRun) { scrollState, synchronizer ->
            synchronizer.scrollToLine(1)
            val packageTop = scrollState.value
            assertTrue(packageTop > 0)

            synchronizer.scrollToLine(2)
            val emptyLineTop = scrollState.value
            assertTrue(emptyLineTop > packageTop)

            synchronizer.scrollToLine(3)
            val mainTop = scrollState.value
            assertTrue(mainTop > emptyLineTop)

            synchronizer.scrollToLine(4)
            val valTop = scrollState.value
            assertTrue(valTop > mainTop)

            synchronizer.scrollToLine(5)
            val printlnTop = scrollState.value
            assertTrue(printlnTop > mainTop)

            synchronizer.scrollToLine(6)
            val rBracketTop = scrollState.value
            assertTrue(rBracketTop > printlnTop)

            synchronizer.scrollToLine(2)
            assertEquals(emptyLineTop, scrollState.value)

            assertSameDistance(
                distance = CODE_TEXT_SIZE + 2,
                packageTop,
                emptyLineTop,
                mainTop,
                valTop,
                printlnTop,
                rBracketTop,
            )
        }
    }

    @Test
    public fun `merge code blocks`() {
        @Language("Markdown")
        val firstRun =
            """
            |```kotlin
            |package my.awesome.pkg
            |
            |fun main() {
            |    println("Hello world")
            |}
            |```
            |
            |```kotlin
            |fun foo() {
            |    println("Foo")
            |}
            |```
            """
                .trimMargin()

        @Language("Markdown")
        val secondRun =
            """
            |```kotlin
            |package my.awesome.pkg
            |
            |fun main() {
            |    println("Hello world")
            |}
            |
            |fun foo() {
            |    println("Foo")
            |}
            |```
            """
                .trimMargin()

        doTest(firstRun, secondRun) { scrollState, synchronizer ->
            synchronizer.scrollToLine(1)
            val packageTop = scrollState.value
            assertTrue(packageTop > 0)

            synchronizer.scrollToLine(2)
            val emptyLine1Top = scrollState.value
            assertTrue(emptyLine1Top > packageTop)

            synchronizer.scrollToLine(3)
            val mainTop = scrollState.value
            assertTrue(mainTop > emptyLine1Top)

            synchronizer.scrollToLine(4)
            val println1Top = scrollState.value
            assertTrue(println1Top > mainTop)

            synchronizer.scrollToLine(5)
            val rBracket1Top = scrollState.value
            assertTrue(rBracket1Top > println1Top)

            synchronizer.scrollToLine(6)
            val emptyLine2Top = scrollState.value
            assertTrue(emptyLine2Top > rBracket1Top)

            synchronizer.scrollToLine(7)
            val fooTop = scrollState.value
            assertTrue(fooTop > emptyLine2Top)

            synchronizer.scrollToLine(8)
            val println2Top = scrollState.value
            assertTrue(println2Top > fooTop)

            synchronizer.scrollToLine(9)
            val rBracket2Top = scrollState.value
            assertTrue(rBracket2Top > println2Top)

            synchronizer.scrollToLine(2)
            assertEquals(emptyLine1Top, scrollState.value)

            assertSameDistance(
                distance = CODE_TEXT_SIZE + 2,
                packageTop,
                emptyLine1Top,
                mainTop,
                println1Top,
                rBracket1Top,
                emptyLine2Top,
                fooTop,
                println2Top,
                rBracket2Top,
            )
        }
    }

    private fun assertSameDistance(distance: Int, vararg elements: Int) {
        assertTrue(elements.size > 1)
        for (i in 0..<elements.lastIndex) {
            assertEquals(elements.contentToString(), distance, elements[i + 1] - elements[i])
        }
    }

    @Test
    public fun `identical items`() {
        val markdown =
            """
                |Items:
                |- item
                |
                |Another items:
                |- item
                        """
                .trimMargin()
        doTest(markdown) { scrollState, synchronizer ->
            synchronizer.scrollToLine(1)
            val l1Top = scrollState.value
            assertTrue(l1Top > 0)

            synchronizer.scrollToLine(2)
            val sl1Top = scrollState.value
            assertTrue(sl1Top > l1Top)

            synchronizer.scrollToLine(3)
            val emptyTop = scrollState.value
            assertTrue(emptyTop == sl1Top)

            synchronizer.scrollToLine(4)
            val l2Top = scrollState.value
            assertTrue(l2Top > emptyTop)

            synchronizer.scrollToLine(4)
            val sl2Top = scrollState.value
            assertTrue(sl2Top == l2Top)
        }
    }

    @Test
    public fun `continuous offsets pass through block tops and interpolate between them`() {
        doTest(threeParagraphs) { scrollState, synchronizer ->
            synchronizer.scrollToLine(2)
            val p2Top = scrollState.value
            synchronizer.scrollToLine(4)
            val p3Top = scrollState.value
            assertTrue(p3Top > p2Top)

            // Each block top is reached exactly at its own source line's editor offset, and maps back to it
            for ((editorOffset, previewOffset) in listOf(0 to 0, 20 to p2Top, 40 to p3Top)) {
                synchronizer.scrollPreviewTo(editorOffset, EDITOR_MAX_OFFSET, tenPixelsPerLine)
                assertEquals(previewOffset, scrollState.value)
                assertEquals(editorOffset, synchronizer.editorOffsetAtPreview(EDITOR_MAX_OFFSET, tenPixelsPerLine))
            }

            // Halfway between two block tops in the editor is halfway between them in the preview, and back
            synchronizer.scrollPreviewTo(30, EDITOR_MAX_OFFSET, tenPixelsPerLine)
            assertTrue(abs(scrollState.value - (p2Top + p3Top) / 2) <= 1)
            assertTrue(abs(synchronizer.editorOffsetAtPreview(EDITOR_MAX_OFFSET, tenPixelsPerLine) - 30) <= 1)
        }
    }

    @Test
    public fun `duplicate editor offsets break ties by preview offset, same as the old sorted anchor list did`() {
        doTest(threeParagraphs) { scrollState, synchronizer ->
            synchronizer.scrollToLine(2)
            val p2Top = scrollState.value
            synchronizer.scrollToLine(4)
            val p3Top = scrollState.value
            assertTrue(p3Top > p2Top)

            // p2 and p3 share one editor offset; p1's line is unknown, and Int.MAX_VALUE keeps the panes' ends from
            // pairing up into a synthetic anchor of their own, so nothing else can bracket the tie
            val dup = { line: Int -> if (line == 2 || line == 4) 20 else null }

            // At the tie, the closest anchor at or above it is the one with the lower preview offset
            synchronizer.scrollPreviewTo(20, Int.MAX_VALUE, dup)
            assertEquals(p2Top, scrollState.value)

            // Just past the tie, with nothing beyond it, the closest anchor below it is the one with the higher
            // preview offset
            synchronizer.scrollPreviewTo(21, Int.MAX_VALUE, dup)
            assertEquals(p3Top, scrollState.value)
        }
    }

    @Test
    public fun `continuous offsets skip blocks whose line the editor does not know`() {
        @Language("Markdown")
        val markdown =
            """
            |p1
            |
            |p2
            """
                .trimMargin()
        doTest(markdown) { scrollState, synchronizer ->
            synchronizer.scrollToLine(2)
            val p2Top = scrollState.value

            // Only p2's line is known, so p1 contributes no anchor and p2's top is reached exactly at its own offset
            val p2LineOnly = { line: Int -> 50.takeIf { line == 2 } }
            synchronizer.scrollPreviewTo(50, 100, p2LineOnly)
            assertEquals(p2Top, scrollState.value)
            assertEquals(50, synchronizer.editorOffsetAtPreview(100, p2LineOnly))
        }
    }

    @Test
    public fun `a pane that has not been measured yet is never scrolled past the blocks it knows about`() {
        doTest(threeParagraphs) { scrollState, synchronizer ->
            synchronizer.scrollToLine(4)
            val p3Top = scrollState.value
            assertTrue(p3Top > 0)
            scrollState.scrollTo(0)

            // This preview is never measured, so its maxValue is still Int.MAX_VALUE. Pairing the panes' ends would
            // send it to an offset it can't hold, and ScrollState overflows to -1 scrolling back down from there.
            synchronizer.scrollPreviewTo(100_000, 1_000, tenPixelsPerLine)
            assertEquals(p3Top, scrollState.value)

            synchronizer.scrollPreviewTo(0, 1_000, tenPixelsPerLine)
            assertEquals(0, scrollState.value)
        }
    }

    @Test
    public fun `sync scrolling drives each pane from the other, and survives the user driving one`() {
        doTest(threeParagraphs) { previewScrollState, synchronizer ->
            val editorScrollState = ScrollState(0)
            coroutineScope {
                val sync = launch { synchronizer.syncScrolling(editorScrollState) { tenPixelsPerLine } }
                settle()

                // The editor drives the preview down to p3 and back to the top
                editorScrollState.scrollTo(40)
                settle()
                val p3Top = previewScrollState.value
                assertTrue(p3Top > 0)
                editorScrollState.scrollTo(0)
                settle()
                assertEquals(0, previewScrollState.value)

                // A gesture holds that pane's scroll mutex at UserInput for its whole duration -- a mouse wheel keeps
                // it for the entire smooth-scroll animation, not just one frame
                val gestureOver = CompletableDeferred<Unit>()
                val gesture = launch { editorScrollState.scroll(MutatePriority.UserInput) { gestureOver.await() } }
                settle()

                // Meanwhile the preview moves, so the sync wants to move the editor -- and is refused, because it
                // only asks at MutatePriority.Default
                previewScrollState.scrollTo(p3Top)
                settle()
                assertEquals(0, editorScrollState.value)
                gestureOver.complete(Unit)
                gesture.join()
                settle()

                // Being refused once must not take that direction down for good: the next scroll resyncs, and the
                // preview drives the editor down and back to the top
                previewScrollState.scrollTo(0)
                settle()
                previewScrollState.scrollTo(p3Top)
                settle()
                assertEquals(40, editorScrollState.value)
                previewScrollState.scrollTo(0)
                settle()
                assertEquals(0, editorScrollState.value)

                sync.cancel()
            }
        }
    }

    @Test
    public fun `a preview below the top of the composition behaves like one at the top`() {
        @Language("Markdown")
        val markdown =
            """
            |# Heading 1
            |
            |p1
            |
            |## Heading 2
            |
            |p2
            """
                .trimMargin()

        // Both the block-by-block and the continuous mapping subtract the preview's distance from the root
        suspend fun probe(scrollState: ScrollState, synchronizer: ContinuousScrollingSynchronizer) = buildList {
            for (line in 0..6) {
                synchronizer.scrollToLine(line)
                add(scrollState.value)
            }
            for (editorOffset in listOf(0, 15, 20, 30, 40)) {
                synchronizer.scrollPreviewTo(editorOffset, EDITOR_MAX_OFFSET, tenPixelsPerLine)
                add(scrollState.value)
                add(synchronizer.editorOffsetAtPreview(EDITOR_MAX_OFFSET, tenPixelsPerLine))
            }
        }

        val atTop = mutableListOf<Int>()
        doTest(markdown) { scrollState, synchronizer -> atTop += probe(scrollState, synchronizer) }

        val belowTop = mutableListOf<Int>()
        doTest(markdown, topInset = INSET.dp) { scrollState, synchronizer ->
            belowTop += probe(scrollState, synchronizer)
        }

        // Without acceptContentPosition every target would be off by the preview's distance from the root
        assertTrue("the preview never moved, so this would pass either way", atTop.any { it > 0 })
        assertEquals(atTop, belowTop)
    }

    @Language("Markdown")
    private val threeParagraphs =
        """
        |p1
        |
        |p2
        |
        |p3
        """
            .trimMargin()

    private fun doTest(
        firstRun: String,
        secondRun: String,
        action: suspend (ScrollState, ContinuousScrollingSynchronizer) -> Unit,
    ) {
        doTest(
            yieldBlocks = {
                processMarkdownDocument(firstRun)
                processMarkdownDocument(secondRun)
            },
            action = action,
        )
    }

    private fun doTest(
        markdown: String,
        topInset: Dp = 0.dp,
        action: suspend (ScrollState, ContinuousScrollingSynchronizer) -> Unit,
    ) {
        doTest(yieldBlocks = { processMarkdownDocument(markdown) }, topInset = topInset, action = action)
    }

    @Suppress("ImplicitUnitReturnType")
    private fun doTest(
        yieldBlocks: MarkdownProcessor.() -> List<MarkdownBlock>,
        topInset: Dp = 0.dp,
        action: suspend (ScrollState, ContinuousScrollingSynchronizer) -> Unit,
    ) =
        runScrollSyncTest(
            scrollState = ScrollState(0),
            styling =
                createMarkdownTestStyling(codeEditorTextStyle = TextStyle.Default.copy(fontSize = CODE_TEXT_SIZE.sp)),
            parseEmbeddedHtml = true,
            // Through Markdown rather than the renderer, because that is what tells the synchronizer where the preview
            // is. [topInset] puts the preview somewhere other than the top of the composition.
            content = { processor ->
                Column {
                    Spacer(Modifier.height(topInset))
                    Markdown(markdownBlocks = processor.yieldBlocks(), markdown = "")
                }
            },
            action = action,
        )

    public companion object {
        private const val CODE_TEXT_SIZE = 10

        // Density is 1, so this is also the preview's distance from the top of the composition in pixels
        private const val INSET = 100

        private const val EDITOR_MAX_OFFSET = 1_000
    }
}
