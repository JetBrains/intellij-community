// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the
// Apache 2.0 license.
package org.jetbrains.jewel.markdown.rendering

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.jewel.markdown.InlineMarkdown
import org.jetbrains.jewel.markdown.MarkdownBlock
import org.jetbrains.jewel.markdown.WithInlineMarkdown
import org.jetbrains.jewel.markdown.extensions.ImageRenderResult
import org.jetbrains.jewel.markdown.extensions.ImageRendererExtension
import org.jetbrains.jewel.markdown.extensions.MarkdownRendererExtension
import org.jetbrains.jewel.markdown.processing.MarkdownProcessor
import org.jetbrains.jewel.markdown.testing.MarkdownTestTheme
import org.jetbrains.jewel.markdown.testing.createMarkdownTestStyling
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

public class DefaultMarkdownBlockRendererTest {
    @Test
    public fun `onUrlClick callback updates when changed`() {
        runComposeUiTest {
            val processor = MarkdownProcessor()
            val markdown = "[Click me](https://example.com)"
            val blocks = processor.processMarkdownDocument(markdown)

            var clickedUrl by mutableStateOf<String?>(null)
            var onUrlClick by mutableStateOf<(String) -> Unit>({ url -> clickedUrl = "first:$url" })

            setContent {
                MarkdownTestTheme {
                    val renderer = DefaultMarkdownBlockRenderer(createMarkdownTestStyling(), emptyList())
                    renderer.RenderBlocks(blocks, enabled = true, onUrlClick = onUrlClick, modifier = Modifier)
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
            val processor = MarkdownProcessor()
            val markdown = "[Click me](https://example.com)"
            val blocks = processor.processMarkdownDocument(markdown)

            var enabled by mutableStateOf(true)
            var clickedUrl by mutableStateOf<String?>(null)

            setContent {
                MarkdownTestTheme {
                    val renderer = DefaultMarkdownBlockRenderer(createMarkdownTestStyling(), emptyList())
                    renderer.RenderBlocks(
                        blocks,
                        enabled = enabled,
                        onUrlClick = { url -> clickedUrl = url },
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
            // When disabled, links should not be clickable
            assertTrue("Link should not be clickable when disabled", clickedUrl == null)
        }
    }

    @Test
    public fun `rendered content updates when both enabled and onUrlClick change simultaneously`() {
        runComposeUiTest {
            val processor = MarkdownProcessor()
            val markdown = "[Click me](https://example.com)"
            val blocks = processor.processMarkdownDocument(markdown)

            var enabled by mutableStateOf(false)
            var clickedUrl by mutableStateOf<String?>(null)
            var onUrlClick by mutableStateOf<(String) -> Unit>({ url -> clickedUrl = "first:$url" })

            setContent {
                MarkdownTestTheme {
                    val renderer = DefaultMarkdownBlockRenderer(createMarkdownTestStyling(), emptyList())
                    renderer.RenderBlocks(blocks, enabled = enabled, onUrlClick = onUrlClick, modifier = Modifier)
                }
            }

            // Initially disabled - click should not work
            onNodeWithText("Click me").performClick()
            waitForIdle()
            assertTrue("Link should not be clickable when disabled", clickedUrl == null)

            // Enable and change callback simultaneously
            enabled = true
            onUrlClick = { url -> clickedUrl = "second:$url" }
            waitForIdle()

            onNodeWithText("Click me").performClick()
            waitForIdle()
            assertEquals("second:https://example.com", clickedUrl)
        }
    }

    @Test
    public fun `raw HTML blocks are not rendered by default`() {
        runComposeUiTest {
            // parseEmbeddedHtml defaults to false: raw HTML blocks are dropped from the model entirely,
            // so they can't leak into the rendered output as raw text.
            val processor = MarkdownProcessor(parseEmbeddedHtml = false)
            val blocks = processor.processMarkdownDocument("<div>Raw HTML</div>\n\nVisible paragraph")

            assertEquals(1, blocks.size)
            assertTrue(blocks[0] is MarkdownBlock.Paragraph)

            setContent {
                MarkdownTestTheme {
                    val renderer = DefaultMarkdownBlockRenderer(createMarkdownTestStyling(), emptyList())
                    renderer.RenderBlocks(blocks, enabled = true, onUrlClick = {}, modifier = Modifier)
                }
            }

            onAllNodesWithText("<div>Raw HTML</div>").assertCountEquals(0)
            onAllNodesWithText("Raw HTML").assertCountEquals(0)
            onNodeWithText("Visible paragraph").assertExists()
        }
    }

    @Test
    public fun `appendInlineContent uses expected annotation tag`() {
        val annotated = buildAnnotatedString { appendInlineContent("testId") }
        val annotations = annotated.getStringAnnotations(COMPOSE_INLINE_CONTENT_ANNOTATION_TAG, 0, annotated.length)
        assertEquals(1, annotations.size)
    }

    @Test
    public fun `blocks without inline images skip image resolution and keep their text`() {
        runComposeUiTest {
            val blocks = MarkdownProcessor().processMarkdownDocument("# Plain heading\n\nPlain paragraph")
            val imageRenderer = RecordingImageRenderer(ImageRenderResult.Failed)
            val renderer = RecordingBlockRenderer(imageRenderer)

            setContent {
                MarkdownTestTheme {
                    renderer.RenderBlocks(blocks, enabled = true, onUrlClick = {}, modifier = Modifier)
                }
            }

            onNodeWithText("Plain heading").assertExists()
            onNodeWithText("Plain paragraph").assertExists()
            assertTrue("resolveImages must not run for blocks without images", renderer.resolvedBlocks.isEmpty())
            assertTrue("No image must be requested", imageRenderer.requestedSources.isEmpty())
        }
    }

    @Test
    public fun `blocks with inline images resolve and show the loaded image`() {
        runComposeUiTest {
            val blocks = MarkdownProcessor().processMarkdownDocument("Text ![alt text]($IMAGE_SOURCE)")
            val loadedContent =
                InlineTextContent(Placeholder(10.sp, 10.sp, PlaceholderVerticalAlign.Center)) {
                    Box(Modifier.size(10.dp).testTag(LOADED_IMAGE_TAG))
                }
            val imageRenderer = RecordingImageRenderer(ImageRenderResult.Success(loadedContent))
            val renderer = RecordingBlockRenderer(imageRenderer)

            setContent {
                MarkdownTestTheme {
                    renderer.RenderBlocks(blocks, enabled = true, onUrlClick = {}, modifier = Modifier)
                }
            }

            onNodeWithTag(LOADED_IMAGE_TAG, useUnmergedTree = true).assertExists()
            assertEquals(1, renderer.resolvedBlocks.distinct().size)
            assertEquals(setOf(IMAGE_SOURCE), imageRenderer.requestedSources.toSet())
        }
    }

    @Test
    public fun `failed inline image renders as a fallback link`() {
        runComposeUiTest {
            val blocks = MarkdownProcessor().processMarkdownDocument("Text ![alt text]($IMAGE_SOURCE)")
            val renderer = RecordingBlockRenderer(RecordingImageRenderer(ImageRenderResult.Failed))

            setContent {
                MarkdownTestTheme {
                    renderer.RenderBlocks(blocks, enabled = true, onUrlClick = {}, modifier = Modifier)
                }
            }

            onNodeWithText("${BROKEN_IMAGE_INDICATOR}alt text", substring = true).assertExists()
            assertTrue("resolveImages must run for blocks with images", renderer.resolvedBlocks.isNotEmpty())
        }
    }

    @Test
    public fun `block that gains and then loses an inline image updates its rendering`() {
        runComposeUiTest {
            val processor = MarkdownProcessor()
            var markdown by mutableStateOf("Text before")
            val renderer = RecordingBlockRenderer(RecordingImageRenderer(ImageRenderResult.Failed))

            setContent {
                MarkdownTestTheme {
                    val blocks = remember(markdown) { processor.processMarkdownDocument(markdown) }
                    renderer.RenderBlocks(blocks, enabled = true, onUrlClick = {}, modifier = Modifier)
                }
            }

            onNodeWithText("Text before").assertExists()
            assertTrue(renderer.resolvedBlocks.isEmpty())

            markdown = "Text before ![alt text]($IMAGE_SOURCE)"
            waitForIdle()

            onNodeWithText("${BROKEN_IMAGE_INDICATOR}alt text", substring = true).assertExists()
            assertTrue(renderer.resolvedBlocks.isNotEmpty())

            markdown = "Text after"
            waitForIdle()

            onNodeWithText("Text after").assertExists()
            onAllNodesWithText(BROKEN_IMAGE_INDICATOR, substring = true).assertCountEquals(0)
        }
    }

    private class RecordingImageRenderer(private val result: ImageRenderResult) : ImageRendererExtension {
        val requestedSources = mutableListOf<String>()

        @Composable
        override fun renderImage(image: InlineMarkdown.Image): ImageRenderResult {
            requestedSources += image.source
            return result
        }
    }

    private class RecordingBlockRenderer(imageRenderer: ImageRendererExtension) :
        DefaultMarkdownBlockRenderer(
            rootStyling = createMarkdownTestStyling(),
            rendererExtensions =
                listOf(
                    object : MarkdownRendererExtension {
                        override val imageRendererExtension: ImageRendererExtension = imageRenderer
                    }
                ),
        ) {
        val resolvedBlocks = mutableListOf<WithInlineMarkdown>()

        @Composable
        override fun resolveImages(
            blockInlineContent: WithInlineMarkdown
        ): Pair<Map<String, InlineTextContent>, Set<String>> {
            resolvedBlocks += blockInlineContent
            return super.resolveImages(blockInlineContent)
        }
    }

    private companion object {
        const val IMAGE_SOURCE = "https://example.com/image.png"
        const val LOADED_IMAGE_TAG = "loaded-image"
    }
}
