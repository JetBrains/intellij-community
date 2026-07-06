// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the
// Apache 2.0 license.
package org.jetbrains.jewel.markdown.rendering

import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import org.jetbrains.jewel.markdown.processing.MarkdownProcessor
import org.jetbrains.jewel.markdown.testing.MarkdownTestTheme
import org.jetbrains.jewel.markdown.testing.createMarkdownTestStyling
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
public class OrderedListRenderingTest {
    @Test
    public fun `rendering zero-start lists across all number-format levels does not crash`() {
        runComposeUiTest {
            val processor = MarkdownProcessor()
            // Blank lines let each indented sub-list START at 0 (a non-1 start can't interrupt a paragraph, but it can
            // begin a list after a blank line). Levels 0/1/2 -> Decimal/Roman/Alphabetical, each formatting 0.
            val blocks = processor.processMarkdownDocument("0. a\n\n    0. b\n\n        0. c")

            setContent {
                MarkdownTestTheme {
                    val renderer = DefaultMarkdownBlockRenderer(createMarkdownTestStyling(), emptyList())
                    renderer.RenderBlocks(blocks, enabled = true, onUrlClick = {}, modifier = Modifier)
                }
            }

            waitForIdle()
            // All three styles fall back to the literal "0" for a zero value, so every marker renders as "0.".
            onAllNodesWithText("0.").assertCountEquals(3)
            onNodeWithText("a").assertExists()
            onNodeWithText("b").assertExists()
            onNodeWithText("c").assertExists()
        }
    }

    @Test
    public fun `Roman second level renders zero as 0 then resumes with Roman numerals`() {
        runComposeUiTest {
            val processor = MarkdownProcessor()
            // Nested list starting at 0 -> level-1 (Roman) items are numbered 0, 1 -> "0." then "i.".
            val blocks = processor.processMarkdownDocument("0. a\n\n    0. b\n    1. c")

            setContent {
                MarkdownTestTheme {
                    val renderer = DefaultMarkdownBlockRenderer(createMarkdownTestStyling(), emptyList())
                    renderer.RenderBlocks(blocks, enabled = true, onUrlClick = {}, modifier = Modifier)
                }
            }

            waitForIdle()
            onNodeWithText("i.").assertExists() // Roman(1), proving the level actually uses Roman formatting
            onNodeWithText("b").assertExists()
            onNodeWithText("c").assertExists()
        }
    }

    @Test
    public fun `non-one ordered sub-item folds into the parent item's line`() {
        runComposeUiTest {
            val processor = MarkdownProcessor()
            // No blank line before the indented sub-list: "0. c" can't interrupt the "b" paragraph (non-1 start), so
            // it folds into item "b" on the same line. "1. d" does interrupt and becomes the nested Roman item "i.".
            //   1. a
            //   2. b 0. c
            //        i. d
            val blocks = processor.processMarkdownDocument("1. a\n2. b\n    0. c\n    1. d")

            setContent {
                MarkdownTestTheme {
                    val renderer = DefaultMarkdownBlockRenderer(createMarkdownTestStyling(), emptyList())
                    renderer.RenderBlocks(blocks, enabled = true, onUrlClick = {}, modifier = Modifier)
                }
            }

            waitForIdle()
            // The folded text lives in a single content node alongside "b"
            onNode(hasText("b", substring = true).and(hasText("0. c", substring = true))).assertExists()
            // "0. c" is plain text, not a marker, so no standalone "0." marker exists.
            onNodeWithText("0.").assertDoesNotExist()
            // "1. d" interrupted the paragraph and nested as Roman(1) -> "i.".
            onNodeWithText("i.").assertExists()
        }
    }

    @Test
    public fun `ordered list with leading zeros renders the stripped start number`() {
        runComposeUiTest {
            val processor = MarkdownProcessor()
            // CommonMark spec example 268: "003." -> OrderedList(startFrom = 3).
            val blocks = processor.processMarkdownDocument("003. ok")

            setContent {
                MarkdownTestTheme {
                    val renderer = DefaultMarkdownBlockRenderer(createMarkdownTestStyling(), emptyList())
                    renderer.RenderBlocks(blocks, enabled = true, onUrlClick = {}, modifier = Modifier)
                }
            }

            waitForIdle()
            // Leading zeros are stripped at parse time, so the marker is "3.", never "003.".
            onNodeWithText("3.").assertExists()
            onNodeWithText("003.").assertDoesNotExist()
            onNodeWithText("ok").assertExists()
        }
    }
}
