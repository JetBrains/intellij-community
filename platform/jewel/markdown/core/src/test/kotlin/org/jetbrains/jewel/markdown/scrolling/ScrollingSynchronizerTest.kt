// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the
// Apache 2.0 license.
package org.jetbrains.jewel.markdown.scrolling

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.intellij.lang.annotations.Language
import org.jetbrains.jewel.foundation.code.highlighting.LocalCodeHighlighter
import org.jetbrains.jewel.foundation.code.highlighting.NoOpCodeHighlighter
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.markdown.MarkdownBlock
import org.jetbrains.jewel.markdown.MarkdownMode
import org.jetbrains.jewel.markdown.extensions.LocalMarkdownBlockRenderer
import org.jetbrains.jewel.markdown.extensions.LocalMarkdownMode
import org.jetbrains.jewel.markdown.extensions.LocalMarkdownProcessor
import org.jetbrains.jewel.markdown.extensions.LocalMarkdownStyling
import org.jetbrains.jewel.markdown.processing.MarkdownProcessor
import org.jetbrains.jewel.markdown.rendering.DefaultInlineMarkdownRenderer
import org.jetbrains.jewel.markdown.rendering.MarkdownStyling
import org.jetbrains.jewel.markdown.testing.createMarkdownTestDividerStyle
import org.jetbrains.jewel.markdown.testing.createMarkdownTestScrollbarStyle
import org.jetbrains.jewel.markdown.testing.createMarkdownTestStyling
import org.jetbrains.jewel.markdown.testing.createMarkdownTestThemeDefinition
import org.jetbrains.jewel.ui.component.styling.LocalDividerStyle
import org.jetbrains.jewel.ui.component.styling.LocalScrollbarStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
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
        @Language("Markdown")
        val markdown =
            """
            |p1
            |
            |p2
            |
            |p3
            """
                .trimMargin()
        doTest(markdown) { scrollState, synchronizer ->
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

    @OptIn(ExperimentalTestApi::class)
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
        @Language("Markdown")
        val markdown =
            """
            |p1
            |
            |p2
            |
            |p3
            """
                .trimMargin()
        doTest(markdown) { scrollState, synchronizer ->
            synchronizer.scrollToLine(2)
            val p2Top = scrollState.value
            synchronizer.scrollToLine(4)
            val p3Top = scrollState.value
            assertTrue(p3Top > p2Top)

            // Every source line is 10px tall in the editor, so p2 starts at 20 and p3 at 40
            val editorOffsetOfLine = { line: Int -> line * 10 }
            val editorMaxOffset = 1000

            assertEquals(0, synchronizer.previewOffsetAt(0, editorMaxOffset, editorOffsetOfLine))
            assertEquals(p2Top, synchronizer.previewOffsetAt(20, editorMaxOffset, editorOffsetOfLine))
            assertEquals(p3Top, synchronizer.previewOffsetAt(40, editorMaxOffset, editorOffsetOfLine))
            val halfway = synchronizer.previewOffsetAt(30, editorMaxOffset, editorOffsetOfLine)
            assertTrue(abs(halfway - (p2Top + p3Top) / 2) <= 1)

            assertEquals(20, synchronizer.editorOffsetAt(p2Top, editorMaxOffset, editorOffsetOfLine))
            assertEquals(40, synchronizer.editorOffsetAt(p3Top, editorMaxOffset, editorOffsetOfLine))
            assertTrue(abs(synchronizer.editorOffsetAt(halfway, editorMaxOffset, editorOffsetOfLine) - 30) <= 1)
        }
    }

    @Test
    public fun `continuous offsets ignore lines the editor does not know`() {
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
            val editorMaxOffset = 100
            // No anchors at all: offsets map proportionally between the two panes' ends
            val unknownLines = { _: Int -> null }
            assertEquals(0, synchronizer.previewOffsetAt(0, editorMaxOffset, unknownLines))
            assertEquals(
                scrollState.maxValue,
                synchronizer.previewOffsetAt(editorMaxOffset, editorMaxOffset, unknownLines),
            )
            // With p2 known, its top is reached exactly at its editor offset
            assertEquals(p2Top, synchronizer.previewOffsetAt(50, editorMaxOffset) { line -> 50.takeIf { line == 2 } })
        }
    }

    @Test
    public fun `sync scrolling follows a pane back to the top after the other pane drove it there`() {
        @Language("Markdown")
        val markdown =
            """
            |p1
            |
            |p2
            |
            |p3
            """
                .trimMargin()
        doTest(markdown) { previewScrollState, synchronizer ->
            val editorScrollState = ScrollState(0)
            val editorOffsetOfLine = { line: Int -> line * 10 }
            coroutineScope {
                val sync = launch {
                    synchronizer.syncScrolling(editorScrollState, previewScrollState) { editorOffsetOfLine }
                }

                suspend fun settle() = repeat(5) { withFrameNanos {} }
                settle()

                // The editor drives the preview down to p3 and back to the top...
                editorScrollState.scrollTo(40)
                settle()
                val p3Top = previewScrollState.value
                assertTrue(p3Top > 0)
                editorScrollState.scrollTo(0)
                settle()
                assertEquals(0, previewScrollState.value)

                // ...then the preview drives the editor down and back to the top. The preview's last report of 0
                // must not be mistaken for an echo of the editor's earlier request.
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

    private fun doTest(
        firstRun: String,
        secondRun: String,
        action: suspend (ScrollState, ScrollingSynchronizer) -> Unit,
    ) {
        doTest(
            yieldBlocks = {
                processMarkdownDocument(firstRun)
                processMarkdownDocument(secondRun)
            },
            action = action,
        )
    }

    private fun doTest(markdown: String, action: suspend (ScrollState, ScrollingSynchronizer) -> Unit) {
        doTest(yieldBlocks = { processMarkdownDocument(markdown) }, action = action)
    }

    @Suppress("ImplicitUnitReturnType")
    @OptIn(ExperimentalTestApi::class)
    private fun doTest(
        yieldBlocks: MarkdownProcessor.() -> List<MarkdownBlock>,
        action: suspend (ScrollState, ScrollingSynchronizer) -> Unit,
    ) = runComposeUiTest {
        val scrollState = ScrollState(0)
        val synchronizer = ScrollingSynchronizer.create(scrollState)!!
        val markdownStyling: MarkdownStyling =
            createMarkdownTestStyling(codeEditorTextStyle = TextStyle.Default.copy(fontSize = CODE_TEXT_SIZE.sp))
        val renderer =
            ScrollSyncMarkdownBlockRenderer(markdownStyling, emptyList(), DefaultInlineMarkdownRenderer(emptyList()))
        val processor =
            MarkdownProcessor(markdownMode = MarkdownMode.EditorPreview(synchronizer), parseEmbeddedHtml = true)
        var scope: CoroutineScope? = null

        setContent {
            scope = rememberCoroutineScope()
            CompositionLocalProvider(
                LocalMarkdownStyling provides markdownStyling,
                LocalMarkdownMode provides MarkdownMode.EditorPreview(synchronizer),
                LocalMarkdownProcessor provides processor,
                LocalMarkdownBlockRenderer provides renderer,
                LocalCodeHighlighter provides NoOpCodeHighlighter,
                LocalDividerStyle provides createMarkdownTestDividerStyle(),
                LocalScrollbarStyle provides createMarkdownTestScrollbarStyle(),
                LocalDensity provides Density(1f),
            ) {
                JewelTheme(createMarkdownTestThemeDefinition()) {
                    val blocks = processor.yieldBlocks()
                    renderer.RenderBlocks(blocks, true, {}, Modifier)
                }
            }
        }

        scope!!.launch { action(scrollState, synchronizer) }
        waitForIdle()
    }

    public companion object {
        private const val CODE_TEXT_SIZE = 10
    }
}
