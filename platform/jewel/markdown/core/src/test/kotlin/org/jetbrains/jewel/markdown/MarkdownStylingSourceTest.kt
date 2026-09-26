// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.markdown

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import org.jetbrains.jewel.markdown.extensions.LocalMarkdownBlockRenderer
import org.jetbrains.jewel.markdown.extensions.LocalMarkdownProcessor
import org.jetbrains.jewel.markdown.extensions.LocalMarkdownStyling
import org.jetbrains.jewel.markdown.extensions.MarkdownRendererExtension
import org.jetbrains.jewel.markdown.processing.MarkdownProcessor
import org.jetbrains.jewel.markdown.rendering.DefaultMarkdownBlockRenderer
import org.jetbrains.jewel.markdown.rendering.InlineMarkdownRenderer
import org.jetbrains.jewel.markdown.rendering.MarkdownBlockRenderer
import org.jetbrains.jewel.markdown.rendering.MarkdownStyling
import org.jetbrains.jewel.markdown.testing.MarkdownTestTheme
import org.jetbrains.jewel.markdown.testing.createMarkdownTestStyling
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests that the styling used when rendering comes from the [MarkdownBlockRenderer], and not from a second, independent
 * source that could disagree with it.
 *
 * The observable proxy for "which styling was used" is the vertical gap between two blocks, since
 * `blockVerticalSpacing` is the one styling value these composables read directly.
 */
@OptIn(ExperimentalTestApi::class)
public class MarkdownStylingSourceTest {
    @Test
    public fun `Markdown takes block spacing from the renderer, not from the styling composition local`() {
        runComposeUiTest {
            val blocks = MarkdownProcessor().processMarkdownDocument(TWO_PARAGRAPHS)
            // The local styling and the renderer's styling deliberately disagree, so the gap tells us which one won.
            val renderer = DefaultMarkdownBlockRenderer(createMarkdownTestStyling(blockVerticalSpacing = RENDERER_GAP))
            val localStyling = createMarkdownTestStyling(blockVerticalSpacing = LOCAL_GAP)

            setContent {
                MarkdownTestTheme {
                    CompositionLocalProvider(
                        LocalMarkdownStyling provides localStyling,
                        LocalMarkdownProcessor provides MarkdownProcessor(),
                        LocalMarkdownBlockRenderer provides renderer,
                    ) {
                        Markdown(markdownBlocks = blocks, markdown = TWO_PARAGRAPHS)
                    }
                }
            }

            waitForIdle()
            assertGapBetweenParagraphs(RENDERER_GAP)
        }
    }

    @Test
    public fun `LazyMarkdown takes block spacing from the renderer, not from the styling composition local`() {
        runComposeUiTest {
            val blocks = MarkdownProcessor().processMarkdownDocument(TWO_PARAGRAPHS)
            val renderer = DefaultMarkdownBlockRenderer(createMarkdownTestStyling(blockVerticalSpacing = RENDERER_GAP))
            val localStyling = createMarkdownTestStyling(blockVerticalSpacing = LOCAL_GAP)

            setContent {
                MarkdownTestTheme {
                    CompositionLocalProvider(
                        LocalMarkdownStyling provides localStyling,
                        LocalMarkdownBlockRenderer provides renderer,
                    ) {
                        LazyMarkdown(blocks = blocks)
                    }
                }
            }

            waitForIdle()
            // LazyMarkdown splits the spacing into half above and half below each item, so the total gap still matches.
            assertGapBetweenParagraphs(RENDERER_GAP)
        }
    }

    @Test
    public fun `Markdown uses the styling of an explicitly passed renderer`() {
        runComposeUiTest {
            val blocks = MarkdownProcessor().processMarkdownDocument(TWO_PARAGRAPHS)
            val renderer = DefaultMarkdownBlockRenderer(createMarkdownTestStyling(blockVerticalSpacing = RENDERER_GAP))
            val localStyling = createMarkdownTestStyling(blockVerticalSpacing = LOCAL_GAP)

            setContent {
                MarkdownTestTheme {
                    CompositionLocalProvider(LocalMarkdownStyling provides localStyling) {
                        // No styling argument, so this binds to the overload without one: the renderer decides.
                        Markdown(markdownBlocks = blocks, markdown = TWO_PARAGRAPHS, blockRenderer = renderer)
                    }
                }
            }

            waitForIdle()
            assertGapBetweenParagraphs(RENDERER_GAP)
        }
    }

    @Test
    @Suppress("DEPRECATION") // Testing the deprecated overload's behavior on purpose
    public fun `deprecated Markdown overload applies the explicit styling to the renderer`() {
        runComposeUiTest {
            val blocks = MarkdownProcessor().processMarkdownDocument(TWO_PARAGRAPHS)
            // The renderer starts with the "wrong" spacing: only re-styling it can produce EXPLICIT_GAP below.
            val renderer = DefaultMarkdownBlockRenderer(createMarkdownTestStyling(blockVerticalSpacing = RENDERER_GAP))
            val explicitStyling = createMarkdownTestStyling(blockVerticalSpacing = EXPLICIT_GAP)

            setContent {
                MarkdownTestTheme {
                    Markdown(
                        markdownBlocks = blocks,
                        markdown = TWO_PARAGRAPHS,
                        markdownStyling = explicitStyling,
                        blockRenderer = renderer,
                    )
                }
            }

            waitForIdle()
            assertGapBetweenParagraphs(EXPLICIT_GAP)
        }
    }

    @Test
    // DEPRECATION: testing the deprecated overload's behavior on purpose. InjectDispatcher: the dispatcher is the point
    // here, as it is what keeps the parsing synchronous and the test deterministic.
    @Suppress("DEPRECATION", "InjectDispatcher")
    public fun `deprecated Markdown string overload applies the explicit styling to the renderer`() {
        runComposeUiTest {
            val renderer = DefaultMarkdownBlockRenderer(createMarkdownTestStyling(blockVerticalSpacing = RENDERER_GAP))
            val explicitStyling = createMarkdownTestStyling(blockVerticalSpacing = EXPLICIT_GAP)

            setContent {
                MarkdownTestTheme {
                    CompositionLocalProvider(LocalMarkdownProcessor provides MarkdownProcessor()) {
                        Markdown(
                            markdown = TWO_PARAGRAPHS,
                            // Unconfined keeps the parsing synchronous, so the blocks are ready once we are idle.
                            processingDispatcher = Dispatchers.Unconfined,
                            markdownStyling = explicitStyling,
                            blockRenderer = renderer,
                        )
                    }
                }
            }

            waitForIdle()
            assertGapBetweenParagraphs(EXPLICIT_GAP)
        }
    }

    @Test
    @Suppress("DEPRECATION") // Testing the deprecated overload's behavior on purpose
    public fun `deprecated LazyMarkdown overload applies the explicit styling to the renderer`() {
        runComposeUiTest {
            val blocks = MarkdownProcessor().processMarkdownDocument(TWO_PARAGRAPHS)
            val renderer = DefaultMarkdownBlockRenderer(createMarkdownTestStyling(blockVerticalSpacing = RENDERER_GAP))
            val explicitStyling = createMarkdownTestStyling(blockVerticalSpacing = EXPLICIT_GAP)

            setContent {
                MarkdownTestTheme {
                    LazyMarkdown(blocks = blocks, markdownStyling = explicitStyling, blockRenderer = renderer)
                }
            }

            waitForIdle()
            assertGapBetweenParagraphs(EXPLICIT_GAP)
        }
    }

    @Test
    @Suppress("DEPRECATION") // Testing the deprecated overload's behavior on purpose
    public fun `the renderer is not copied when the explicit styling already matches it`() {
        runComposeUiTest {
            val blocks = MarkdownProcessor().processMarkdownDocument(TWO_PARAGRAPHS)
            val styling = createMarkdownTestStyling(blockVerticalSpacing = RENDERER_GAP)
            val renderer = CopyCountingBlockRenderer(styling)

            setContent {
                MarkdownTestTheme {
                    // The very same styling instance the renderer already holds: there is nothing to re-style.
                    Markdown(
                        markdownBlocks = blocks,
                        markdown = TWO_PARAGRAPHS,
                        markdownStyling = styling,
                        blockRenderer = renderer,
                    )
                }
            }

            waitForIdle()
            assertEquals("The renderer should have been reused as-is", 0, renderer.copyCount)
            assertGapBetweenParagraphs(RENDERER_GAP)
        }
    }

    @Test
    @Suppress("DEPRECATION") // Testing the deprecated overload's behavior on purpose
    public fun `the renderer is copied once when the explicit styling differs`() {
        runComposeUiTest {
            val blocks = MarkdownProcessor().processMarkdownDocument(TWO_PARAGRAPHS)
            val renderer = CopyCountingBlockRenderer(createMarkdownTestStyling(blockVerticalSpacing = RENDERER_GAP))
            val explicitStyling = createMarkdownTestStyling(blockVerticalSpacing = EXPLICIT_GAP)

            setContent {
                MarkdownTestTheme {
                    Markdown(
                        markdownBlocks = blocks,
                        markdown = TWO_PARAGRAPHS,
                        markdownStyling = explicitStyling,
                        blockRenderer = renderer,
                    )
                }
            }

            waitForIdle()
            assertEquals("The renderer should have been copied exactly once", 1, renderer.copyCount)
            assertGapBetweenParagraphs(EXPLICIT_GAP)
        }
    }

    @Test
    @Suppress("DEPRECATION") // Testing the deprecated overload's behavior on purpose
    public fun `the styled renderer is reused across recompositions`() {
        runComposeUiTest {
            val blocks = MarkdownProcessor().processMarkdownDocument(TWO_PARAGRAPHS)
            val renderer = CopyCountingBlockRenderer(createMarkdownTestStyling(blockVerticalSpacing = RENDERER_GAP))
            val explicitStyling = createMarkdownTestStyling(blockVerticalSpacing = EXPLICIT_GAP)
            var enabled by mutableStateOf(true)

            setContent {
                MarkdownTestTheme {
                    Markdown(
                        markdownBlocks = blocks,
                        markdown = TWO_PARAGRAPHS,
                        enabled = enabled,
                        markdownStyling = explicitStyling,
                        blockRenderer = renderer,
                    )
                }
            }

            waitForIdle()
            assertEquals("The first composition should copy the renderer once", 1, renderer.copyCount)

            // Force a recomposition: without a remember, the styled copy would be rebuilt every time, which would both
            // churn allocations and hand a brand new renderer identity to everything downstream.
            enabled = false
            waitForIdle()

            assertEquals("Recomposing should not rebuild the styled renderer", 1, renderer.copyCount)
        }
    }

    private fun ComposeUiTest.assertGapBetweenParagraphs(expected: Dp) {
        val first = onNodeWithText(FIRST_PARAGRAPH).getBoundsInRoot()
        val second = onNodeWithText(SECOND_PARAGRAPH).getBoundsInRoot()
        val actual = second.top - first.bottom

        assertEquals("Unexpected gap between the two paragraphs", expected.value, actual.value, TOLERANCE)
    }

    private companion object {
        private const val FIRST_PARAGRAPH = "Alpha"
        private const val SECOND_PARAGRAPH = "Bravo"
        private const val TWO_PARAGRAPHS = "$FIRST_PARAGRAPH\n\n$SECOND_PARAGRAPH"

        /** Rounding when converting between Dp and pixels can cost a fraction of a Dp. */
        private const val TOLERANCE = 1f

        private val LOCAL_GAP = 7.dp
        private val RENDERER_GAP = 31.dp
        private val EXPLICIT_GAP = 53.dp
    }
}

/** A [DefaultMarkdownBlockRenderer] that counts how many times it has been copied via [createCopy]. */
private class CopyCountingBlockRenderer(styling: MarkdownStyling) : DefaultMarkdownBlockRenderer(styling) {
    var copyCount: Int = 0
        private set

    override fun createCopy(
        rootStyling: MarkdownStyling?,
        rendererExtensions: List<MarkdownRendererExtension>?,
        inlineRenderer: InlineMarkdownRenderer?,
    ): MarkdownBlockRenderer {
        copyCount++
        return super.createCopy(rootStyling, rendererExtensions, inlineRenderer)
    }
}
