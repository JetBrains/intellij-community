// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the
// Apache 2.0 license.
package org.jetbrains.jewel.markdown.extensions.github.tables

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.markdown.processing.MarkdownProcessor
import org.jetbrains.jewel.markdown.rendering.DefaultMarkdownBlockRenderer
import org.jetbrains.jewel.markdown.testing.MarkdownTestTheme
import org.jetbrains.jewel.markdown.testing.createMarkdownTestStyling
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
public class GitHubTableBlockRendererRememberKeysTest {
    @Test
    public fun `onUrlClick callback updates when changed`() {
        runComposeUiTest {
            val markdownStyling = createMarkdownTestStyling()
            val tableStyling = createTableStyling()
            val rendererExtension = GitHubTableRendererExtension(tableStyling, markdownStyling)
            val processor = MarkdownProcessor(extensions = listOf(GitHubTableProcessorExtension))

            val markdown =
                """
            | Header |
            | ------ |
            | [Click me](https://example.com) |
            """
                    .trimIndent()
            val blocks = processor.processMarkdownDocument(markdown)

            var clickedUrl by mutableStateOf<String?>(null)
            var onUrlClick by mutableStateOf<(String) -> Unit>({ url -> clickedUrl = "first:$url" })

            setContent {
                MarkdownTestTheme {
                    val renderer = DefaultMarkdownBlockRenderer(markdownStyling, listOf(rendererExtension))
                    renderer.render(
                        blocks,
                        enabled = true,
                        onUrlClick = onUrlClick,
                        onTextClick = {},
                        modifier = Modifier,
                    )
                }
            }

            onNodeWithText("Click me").performClick()
            waitForIdle()
            assertEquals("first:https://example.com", clickedUrl)

            // Change the callback
            clickedUrl = null
            onUrlClick = { url -> clickedUrl = "second:$url" }
            waitForIdle()

            onNodeWithText("Click me").performClick()
            waitForIdle()
            assertEquals("second:https://example.com", clickedUrl)
        }
    }

    @Test
    public fun `enabled change updates link rendering`() {
        runComposeUiTest {
            val markdownStyling = createMarkdownTestStyling()
            val tableStyling = createTableStyling()
            val rendererExtension = GitHubTableRendererExtension(tableStyling, markdownStyling)
            val processor = MarkdownProcessor(extensions = listOf(GitHubTableProcessorExtension))

            val markdown =
                """
            | Header |
            | ------ |
            | [Click me](https://example.com) |
            """
                    .trimIndent()
            val blocks = processor.processMarkdownDocument(markdown)

            var enabled by mutableStateOf(true)
            var clickedUrl by mutableStateOf<String?>(null)

            setContent {
                MarkdownTestTheme {
                    val renderer = DefaultMarkdownBlockRenderer(markdownStyling, listOf(rendererExtension))
                    renderer.render(
                        blocks,
                        enabled = enabled,
                        onUrlClick = { url -> clickedUrl = url },
                        onTextClick = {},
                        modifier = Modifier,
                    )
                }
            }

            // When enabled, clicking the link should invoke the callback
            onNodeWithText("Click me").performClick()
            waitForIdle()
            assertEquals("https://example.com", clickedUrl)

            // Disable and verify the callback is no longer invoked
            clickedUrl = null
            enabled = false
            waitForIdle()

            onNodeWithText("Click me").performClick()
            waitForIdle()
            assertTrue("Link should not be clickable when disabled", clickedUrl == null)
        }
    }

    private fun createTableStyling() =
        GfmTableStyling(
            colors =
                GfmTableColors(
                    borderColor = Color.Black,
                    rowBackgroundColor = Color.White,
                    alternateRowBackgroundColor = Color.LightGray,
                    rowBackgroundStyle = RowBackgroundStyle.Normal,
                ),
            metrics =
                GfmTableMetrics(
                    borderWidth = 1.dp,
                    cellPadding = PaddingValues(4.dp),
                    defaultCellContentAlignment = Alignment.Start,
                    headerDefaultCellContentAlignment = Alignment.Start,
                ),
            headerBaseFontWeight = FontWeight.Bold,
        )
}
