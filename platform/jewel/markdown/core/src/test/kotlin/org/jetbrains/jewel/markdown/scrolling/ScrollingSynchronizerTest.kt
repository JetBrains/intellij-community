// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the
// Apache 2.0 license.
package org.jetbrains.jewel.markdown.scrolling

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.intellij.lang.annotations.Language
import org.jetbrains.jewel.foundation.BorderColors
import org.jetbrains.jewel.foundation.DisabledAppearanceValues
import org.jetbrains.jewel.foundation.GlobalColors
import org.jetbrains.jewel.foundation.GlobalMetrics
import org.jetbrains.jewel.foundation.OutlineColors
import org.jetbrains.jewel.foundation.TextColors
import org.jetbrains.jewel.foundation.code.highlighting.LocalCodeHighlighter
import org.jetbrains.jewel.foundation.code.highlighting.NoOpCodeHighlighter
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.foundation.theme.ThemeColorPalette
import org.jetbrains.jewel.foundation.theme.ThemeDefinition
import org.jetbrains.jewel.foundation.theme.ThemeIconData
import org.jetbrains.jewel.markdown.MarkdownBlock
import org.jetbrains.jewel.markdown.MarkdownMode
import org.jetbrains.jewel.markdown.extensions.LocalMarkdownBlockRenderer
import org.jetbrains.jewel.markdown.extensions.LocalMarkdownMode
import org.jetbrains.jewel.markdown.extensions.LocalMarkdownProcessor
import org.jetbrains.jewel.markdown.extensions.LocalMarkdownStyling
import org.jetbrains.jewel.markdown.processing.MarkdownProcessor
import org.jetbrains.jewel.markdown.rendering.DefaultInlineMarkdownRenderer
import org.jetbrains.jewel.markdown.rendering.InlinesStyling
import org.jetbrains.jewel.markdown.rendering.MarkdownStyling
import org.jetbrains.jewel.markdown.rendering.MarkdownStyling.List.Ordered.NumberFormatStyles.NumberFormatStyle
import org.jetbrains.jewel.markdown.rendering.MarkdownStyling.List.Unordered.BulletCharStyles
import org.jetbrains.jewel.ui.component.styling.DividerMetrics
import org.jetbrains.jewel.ui.component.styling.DividerStyle
import org.jetbrains.jewel.ui.component.styling.LocalDividerStyle
import org.jetbrains.jewel.ui.component.styling.LocalScrollbarStyle
import org.jetbrains.jewel.ui.component.styling.ScrollbarColors
import org.jetbrains.jewel.ui.component.styling.ScrollbarMetrics
import org.jetbrains.jewel.ui.component.styling.ScrollbarStyle
import org.jetbrains.jewel.ui.component.styling.ScrollbarVisibility
import org.jetbrains.jewel.ui.component.styling.TrackClickBehavior
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

    @OptIn(ExperimentalTestApi::class)
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
        val markdownStyling: MarkdownStyling = createMarkdownStyling()
        val renderer =
            ScrollSyncMarkdownBlockRenderer(markdownStyling, emptyList(), DefaultInlineMarkdownRenderer(emptyList()))
        val processor = MarkdownProcessor(markdownMode = MarkdownMode.EditorPreview(synchronizer))
        var scope: CoroutineScope? = null

        setContent {
            scope = rememberCoroutineScope()
            CompositionLocalProvider(
                LocalMarkdownStyling provides markdownStyling,
                LocalMarkdownMode provides MarkdownMode.EditorPreview(synchronizer),
                LocalMarkdownProcessor provides processor,
                LocalMarkdownBlockRenderer provides renderer,
                LocalCodeHighlighter provides NoOpCodeHighlighter,
                LocalDividerStyle provides createDividerStyle(),
                LocalScrollbarStyle provides createScrollbarStyle(),
                LocalDensity provides createDensity(),
            ) {
                JewelTheme(createThemeDefinition()) {
                    val blocks = processor.yieldBlocks()
                    renderer.render(blocks, true, {}, {}, Modifier)
                }
            }
        }

        scope!!.launch { action(scrollState, synchronizer) }
        waitForIdle()
    }

    private fun createDividerStyle() =
        DividerStyle(color = Color.Black, metrics = DividerMetrics(thickness = 1.dp, startIndent = 0.dp))

    private fun createScrollbarStyle() =
        ScrollbarStyle(
            colors =
                ScrollbarColors(
                    thumbBackground = Color.Black,
                    thumbBorderActive = Color.Black,
                    thumbBackgroundActive = Color.Black,
                    thumbOpaqueBackground = Color.Black,
                    thumbOpaqueBackgroundHovered = Color.Black,
                    thumbBorder = Color.Black,
                    thumbOpaqueBorder = Color.Black,
                    thumbOpaqueBorderHovered = Color.Black,
                    trackBackground = Color.Black,
                    trackBackgroundExpanded = Color.Black,
                    trackOpaqueBackground = Color.Black,
                    trackOpaqueBackgroundHovered = Color.Black,
                ),
            metrics = ScrollbarMetrics(thumbCornerSize = CornerSize(1.dp), minThumbLength = 1.dp),
            trackClickBehavior = TrackClickBehavior.NextPage,
            scrollbarVisibility =
                ScrollbarVisibility.AlwaysVisible(
                    trackThickness = 1.dp,
                    trackPadding = PaddingValues(1.dp),
                    trackPaddingWithBorder = PaddingValues(1.dp),
                    thumbColorAnimationDuration = 500.milliseconds,
                    trackColorAnimationDuration = 500.milliseconds,
                    scrollbarBackgroundColorLight = Color.White,
                    scrollbarBackgroundColorDark = Color.White,
                ),
        )

    private fun createDensity() = Density(1f)

    private fun createThemeDefinition(): ThemeDefinition {
        return ThemeDefinition(
            name = "Test",
            isDark = false,
            globalColors =
                GlobalColors(
                    borders = BorderColors(normal = Color.Black, focused = Color.Black, disabled = Color.Black),
                    outlines =
                        OutlineColors(
                            focused = Color.Black,
                            focusedWarning = Color.Black,
                            focusedError = Color.Black,
                            warning = Color.Black,
                            error = Color.Black,
                        ),
                    text =
                        TextColors(
                            normal = Color.Black,
                            selected = Color.Black,
                            disabled = Color.Black,
                            disabledSelected = Color.Black,
                            info = Color.Black,
                            error = Color.Black,
                            warning = Color.Black,
                        ),
                    panelBackground = Color.White,
                ),
            globalMetrics = GlobalMetrics(outlineWidth = 10.dp, rowHeight = 24.dp),
            defaultTextStyle = TextStyle.Default,
            editorTextStyle = TextStyle.Default,
            consoleTextStyle = TextStyle.Default,
            contentColor = Color.Black,
            colorPalette = ThemeColorPalette.Empty,
            iconData = ThemeIconData.Empty,
            disabledAppearanceValues = DisabledAppearanceValues(brightness = 33, contrast = -35, alpha = 100),
        )
    }

    private fun createMarkdownStyling(): MarkdownStyling {
        val mockSpanStyle = SpanStyle(Color.Black)
        val inlinesStyling =
            InlinesStyling(
                textStyle = TextStyle.Default,
                inlineCode = mockSpanStyle,
                link = mockSpanStyle,
                linkDisabled = mockSpanStyle,
                linkFocused = mockSpanStyle,
                linkHovered = mockSpanStyle,
                linkPressed = mockSpanStyle,
                linkVisited = mockSpanStyle,
                emphasis = mockSpanStyle,
                strongEmphasis = mockSpanStyle,
                inlineHtml = mockSpanStyle,
            )
        return MarkdownStyling(
            blockVerticalSpacing = 8.dp,
            paragraph = MarkdownStyling.Paragraph(inlinesStyling),
            heading =
                MarkdownStyling.Heading(
                    h1 = MarkdownStyling.Heading.H1(inlinesStyling, 1.dp, Color.Black, 2.dp, PaddingValues(4.dp)),
                    h2 = MarkdownStyling.Heading.H2(inlinesStyling, 1.dp, Color.Black, 2.dp, PaddingValues(4.dp)),
                    h3 = MarkdownStyling.Heading.H3(inlinesStyling, 1.dp, Color.Black, 2.dp, PaddingValues(4.dp)),
                    h4 = MarkdownStyling.Heading.H4(inlinesStyling, 1.dp, Color.Black, 2.dp, PaddingValues(4.dp)),
                    h5 = MarkdownStyling.Heading.H5(inlinesStyling, 1.dp, Color.Black, 2.dp, PaddingValues(4.dp)),
                    h6 = MarkdownStyling.Heading.H6(inlinesStyling, 1.dp, Color.Black, 2.dp, PaddingValues(4.dp)),
                ),
            blockQuote =
                MarkdownStyling.BlockQuote(
                    padding = PaddingValues(4.dp),
                    lineWidth = 2.dp,
                    lineColor = Color.Gray,
                    pathEffect = null,
                    strokeCap = StrokeCap.Square,
                    textColor = Color.Black,
                ),
            code =
                MarkdownStyling.Code(
                    indented =
                        MarkdownStyling.Code.Indented(
                            editorTextStyle = TextStyle.Default.copy(fontSize = CODE_TEXT_SIZE.sp),
                            padding = PaddingValues(4.dp),
                            shape = RectangleShape,
                            background = Color.LightGray,
                            borderWidth = 0.dp,
                            borderColor = Color.DarkGray,
                            fillWidth = true,
                            scrollsHorizontally = true,
                        ),
                    fenced =
                        MarkdownStyling.Code.Fenced(
                            editorTextStyle = TextStyle.Default.copy(fontSize = CODE_TEXT_SIZE.sp),
                            padding = PaddingValues(4.dp),
                            shape = RectangleShape,
                            background = Color.LightGray,
                            borderWidth = 0.dp,
                            borderColor = Color.DarkGray,
                            fillWidth = true,
                            scrollsHorizontally = true,
                            infoTextStyle = TextStyle.Default,
                            infoPadding = PaddingValues(2.dp),
                            infoPosition = MarkdownStyling.Code.Fenced.InfoPosition.TopStart,
                        ),
                ),
            list =
                MarkdownStyling.List(
                    ordered =
                        MarkdownStyling.List.Ordered(
                            numberStyle = TextStyle.Default,
                            numberContentGap = 1.dp,
                            numberMinWidth = 2.dp,
                            numberTextAlign = TextAlign.Start,
                            itemVerticalSpacing = 4.dp,
                            itemVerticalSpacingTight = 2.dp,
                            padding = PaddingValues(4.dp),
                            numberFormatStyles =
                                MarkdownStyling.List.Ordered.NumberFormatStyles(firstLevel = NumberFormatStyle.Decimal),
                        ),
                    unordered =
                        MarkdownStyling.List.Unordered(
                            bullet = '.',
                            bulletStyle = TextStyle.Default,
                            bulletContentGap = 1.dp,
                            itemVerticalSpacing = 4.dp,
                            itemVerticalSpacingTight = 2.dp,
                            padding = PaddingValues(4.dp),
                            markerMinWidth = 16.dp,
                            bulletCharStyles = BulletCharStyles(),
                        ),
                ),
            image =
                MarkdownStyling.Image(
                    alignment = Alignment.Center,
                    contentScale = ContentScale.Crop,
                    padding = PaddingValues(8.dp),
                    shape = RectangleShape,
                    background = Color.Transparent,
                    borderWidth = 1.dp,
                    borderColor = Color.LightGray,
                ),
            thematicBreak =
                MarkdownStyling.ThematicBreak(
                    padding = PaddingValues(4.dp),
                    lineWidth = 2.dp,
                    lineColor = Color.DarkGray,
                ),
            htmlBlock =
                MarkdownStyling.HtmlBlock(
                    textStyle = TextStyle.Default,
                    padding = PaddingValues(4.dp),
                    shape = RectangleShape,
                    background = Color.White,
                    borderWidth = 1.dp,
                    borderColor = Color.Gray,
                    fillWidth = true,
                ),
        )
    }

    public companion object {
        private const val CODE_TEXT_SIZE = 10
    }
}
