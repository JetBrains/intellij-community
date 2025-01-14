// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the
// Apache 2.0 license.
package org.jetbrains.jewel.markdown.scrolling

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.TextLayoutResult
import java.util.TreeMap
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.util.myLogger
import org.jetbrains.jewel.markdown.MarkdownBlock
import org.jetbrains.jewel.markdown.processing.MarkdownProcessor

/**
 * To support synchronized scrolling between source and preview, we need to establish a mapping between source lines and
 * coordinates of their presentation.
 *
 * For simplicity, let's suppose that the source code is immutable. [MarkdownProcessor] parses it and yields a list of
 * [MarkdownBlock]s. Unfortunately, it doesn't contain any information about the source lines, as the need to keep them
 * and reserve more heap is not strong enough (the hypothesis is that most users just need to read the .md file and not
 * to edit it).
 *
 * However, [MarkdownProcessor] uses commonmark inside and takes the blocks this library returns to build
 * [MarkdownBlock]s, and in the editor mode, commonmark blocks still hold the information about source lines.
 * [acceptBlockSpans] can be implemented the way that remembers mappings between [MarkdownBlock]s and source lines these
 * blocks span over.
 *
 * Next, Compose provides the callback [onGloballyPositioned] with precalculated global layout. [acceptGlobalPosition]
 * can be implemented to remember mappings between [MarkdownBlock]s and global coordinates these blocks are rendered on.
 *
 * These two mappings are enough to make the synchronizer work. When a source code is scrolled to a line, an
 * implementation can find a block containing the line (or the next one if there are no blocks on the line), then find
 * this block's global layout and, finally, tell Compose to scroll to the topmost coordinate of the layout. This way, a
 * user can observe the whole block in the preview, even if only a part of it is visible in the source view.
 *
 * For some blocks, however, it makes sense to scroll within their content. Code blocks make for a perfect example of
 * it. They can contain a lot of lines, and at the same time, they're not soft-wrapped in a preview, every source line
 * is mapped 1:1 to the preview, so scrolling inside a code block would be preferable (and natural) to support.
 * [acceptTextLayout] serves the purpose of calculation every line's position within the composable. This information
 * may, in turn, be used together with global positioning of the composable to compute the absolute position of a
 * certain line in the preview.
 *
 * # Editing
 *
 * [MarkdownProcessor] always yields all the blocks that are present in the source, even in optimized mode, so
 * [acceptBlockSpans] is not really affected by editing. [acceptGlobalPosition] is trickier, as it is not triggered on
 * blocks preceding the change. [acceptTextLayout] is even more intricate, as it may or may not be triggered on blocks
 * following the change. It implies that mappings should be adjusted accordingly. [beforeProcessing] and
 * [afterProcessing] can help with that, as they're invoked before and after every re-parse, i.e. every change in the
 * file. See [PerLine] as one of the possible implementations for [ScrollState].
 *
 * # Keep in mind
 * - [acceptBlockSpans] accepts blocks in the **depth-first order**.
 * - Between [beforeProcessing] and [afterProcessing] every single block is processed, [acceptBlockSpans] is triggered
 *   for every one of them.
 * - [acceptGlobalPosition] is **always** triggered on the changed block and the blocks that follow the change.
 * - [acceptTextLayout] is **always** triggered on the changed block, but **not always** on those located below the
 *   changed block. It's **not triggered** on blocks located above the change.
 * - [acceptTextLayout] is triggered **before** [acceptGlobalPosition] for the same block.
 *
 * @see [MarkdownProcessor]
 * @see [AutoScrollableBlock]
 * @see [PerLine]
 */
@ExperimentalJewelApi
public abstract class ScrollingSynchronizer {
    /** Scroll the preview to the position that match the given [sourceLine] the best. */
    public abstract suspend fun scrollToLine(sourceLine: Int)

    /**
     * Called when [MarkdownProcessor] processes the raw markdown text. The processing itself is passed as an [action].
     */
    public fun <T> process(action: () -> T): T {
        beforeProcessing()
        return try {
            action()
        } finally {
            afterProcessing()
        }
    }

    /** Called before [MarkdownProcessor] starts processing the raw markdown text. */
    protected abstract fun beforeProcessing()

    /** Called after [MarkdownProcessor] starts processing the raw markdown text. */
    protected abstract fun afterProcessing()

    /**
     * Accept mapping between the markdown [block] and the [sourceRange] of lines containing this block. Called on every
     * block after it was (re)parsed.
     */
    public abstract fun acceptBlockSpans(block: MarkdownBlock, sourceRange: IntRange)

    /**
     * Accept mapping between the markdown [block] and the global [coordinates] of lines containing this block. Called
     * on all blocks that require (re)positioning: on first composition, on a changed block, on unchanged blocks that
     * are positioned below the changed block.
     */
    public abstract fun acceptGlobalPosition(block: MarkdownBlock, coordinates: LayoutCoordinates)

    /**
     * Accept mapping between the markdown [block] and the [textLayout] of the text this block comprises. Called on all
     * blocks that require adjusting text layout: on first composition, on a block with the changed text, and may be
     * called on unchanged blocks that are positioned below the changed block.
     */
    public abstract fun acceptTextLayout(block: MarkdownBlock, textLayout: TextLayoutResult)

    public companion object {
        public fun create(scrollState: ScrollableState): ScrollingSynchronizer? =
            when (scrollState) {
                is ScrollState -> PerLine(scrollState)
                is LazyListState -> {
                    myLogger().warn("Synchronization for LazyListState is not supported yet")
                    null
                }

                else -> null
            }
    }

    private class PerLine(private val scrollState: ScrollState) : ScrollingSynchronizer() {
        private val lines2Blocks = TreeMap<Int, MarkdownBlock>()
        private var blocks2LineRanges = mutableMapOf<MarkdownBlock, IntRange>()
        private val blocks2Top = mutableMapOf<MarkdownBlock, Int>()
        private val previousPositions = mutableMapOf<MarkdownBlock, Int>()

        // Only used to clean up obsolete keys in the maps above;
        // otherwise stale MarkdownBlocks will keep piling up on each typed key
        private val actualBlocks = mutableSetOf<MarkdownBlock>()

        // It'd be a bit more performant if there were a map mapping lines to offsets,
        // and that was the initial approach,
        // but this structure would be hard to maintain because of optimizations in Compose.
        // Namely, text offsets may not be recalculated even if the block was repositioned.
        // For example, if contents of one item in a Column change, it only causes relayout
        // of the changed item, and not the items that follow, even though they are to be
        // repositioned globally.
        // Thus, even if lines that a block occupies change,
        // relative offsets within the block can remain the same.
        // But here, given there's guaranteed 1:1 source to preview lines mapping,
        // the rules holds that, if a block hasn't changed, text offsets remain unchanged too,
        // so this map always keeps relevant information.
        private val blocks2TextOffsets = mutableMapOf<MarkdownBlock, List<Int>>()

        override suspend fun scrollToLine(sourceLine: Int) {
            val block = findBestBlockForLine(sourceLine) ?: return
            val y = blocks2Top[block] ?: return
            if (y < 0) return
            val lineRange = blocks2LineRanges[block] ?: return
            val textOffsets = blocks2TextOffsets[block]
            // The line may be empty and represent no block,
            // in this case scroll to the first line of the first block positioned after the line
            val lineIndexInBlock = maxOf(0, sourceLine - lineRange.start)
            val lineOffset = textOffsets?.get(lineIndexInBlock) ?: 0
            scrollState.animateScrollTo(y + lineOffset)
        }

        private fun findBestBlockForLine(line: Int): MarkdownBlock? {
            // The best block is the one **below** the line if there is no block that covers the
            // line.
            // Otherwise, when scrolling down the source, on empty lines preview will scroll in the
            // opposite direction
            val sm = lines2Blocks.subMap(line, Int.MAX_VALUE)
            if (sm.isEmpty()) return null
            // TODO use firstEntry() after switching to JDK 21
            return sm.getValue(sm.firstKey())
        }

        override fun beforeProcessing() {
            // acceptBlockSpans works on ALL the nodes, including those unchanged,
            // so it will be fully rebuilt during processing anyway
            lines2Blocks.clear()
            blocks2LineRanges.clear()
        }

        override fun afterProcessing() {
            blocks2LineRanges.keys.retainAll(actualBlocks)
            blocks2Top.keys.retainAll(actualBlocks)
            blocks2TextOffsets.keys.retainAll(actualBlocks)
            previousPositions.keys.retainAll(actualBlocks)
            actualBlocks.clear()
        }

        override fun acceptBlockSpans(block: MarkdownBlock, sourceRange: IntRange) {
            for (line in sourceRange) {
                // DFS -- keep the innermost block for the given line
                lines2Blocks.putIfAbsent(line, block)
            }
            blocks2LineRanges[block] = sourceRange
            actualBlocks += block
        }

        override fun acceptGlobalPosition(block: MarkdownBlock, coordinates: LayoutCoordinates) {
            // coordinates are relative to the current viewport
            // (which also means onPositionedGlobally is triggered when scrolling);
            // to get the real absolute coordinates we need to consider scroll state
            val y = coordinates.positionInRoot().y.toInt() + scrollState.value

            // let's not recalculate internal structures on the preview scrolling -- more safety
            val oldY = previousPositions[block]
            if (oldY == null || y != oldY) {
                blocks2Top[block] = y
                previousPositions[block] = y
            }
        }

        override fun acceptTextLayout(block: MarkdownBlock, textLayout: TextLayoutResult) {
            if (block !is MarkdownBlock.CodeBlock) return
            val sourceLines = blocks2LineRanges[block] ?: return

            var y = 0
            val list = mutableListOf<Int>()

            if (block is MarkdownBlock.CodeBlock.FencedCodeBlock) {
                // All source lines in the fenced code block,
                // beside the first and the last ones, are mapped 1:1 onto preview
                // code block:
                //
                //               |   source:         |       preview:
                // __________________________________|_________________
                // (first line)  |   ```language     |       <no mapping>
                //               |   <line 1>        |       <line 1>
                //               |   <line 2>        |       <line 2>
                //               |   <line 3>        |       <line 3>
                //               |   <line 4>        |       <line 4>
                //               |   <line 5>        |       <line 5>
                //  (last line)  |   ```             |       <no mapping>
                //
                // Some of the lines might be empty, and thus there are no spans for them.
                // However, every empty line follows the 1:1 mapping rule,
                // which means all of the lines in the range [first line + 1; last line - 1]
                // have their counterparts in the preview, regardless of the content.

                val openingLine = sourceLines.first()
                val firstSourceLine = openingLine + 1
                val closingLine = sourceLines.last()
                // map the line with opening triple backticks
                // to the topmost point of the block in the preview
                list += y
                for (i in firstSourceLine..<closingLine) {
                    list += y
                    val lineHeight =
                        textLayout.getLineBottom(i - firstSourceLine) - textLayout.getLineTop(i - firstSourceLine)
                    y += lineHeight.toInt()
                }
                // map the line with closing triple backticks
                // to the bottommost point of the block in the preview
                list += y
            } else if (block is MarkdownBlock.CodeBlock.IndentedCodeBlock) {
                // Indented code blocks don't have the empty last line,
                // and the empty opening line is not counted:
                //
                //               |   source:         |       preview:
                // __________________________________|_________________
                // (first line)  |   <line 1>        |       <line 1>
                //               |   <line 2>        |       <line 2>
                //               |   <line 3>        |       <line 3>
                //               |   <line 4>        |       <line 4>
                // (last line)   |   <line 5>        |       <line 5>
                for (i in sourceLines) {
                    list += y
                    val lineHeight =
                        textLayout.getLineBottom(i - sourceLines.first) - textLayout.getLineTop(i - sourceLines.first)
                    y += lineHeight.toInt()
                }
            }
            blocks2TextOffsets[block] = list
        }
    }
}
