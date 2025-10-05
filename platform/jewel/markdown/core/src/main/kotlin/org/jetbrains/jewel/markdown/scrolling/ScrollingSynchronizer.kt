// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the
// Apache 2.0 license.
package org.jetbrains.jewel.markdown.scrolling

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.TextLayoutResult
import java.util.TreeMap
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.util.myLogger
import org.jetbrains.jewel.markdown.MarkdownBlock
import org.jetbrains.jewel.markdown.WithChildBlocks
import org.jetbrains.jewel.markdown.processing.MarkdownProcessor

/**
 * To support synchronized scrolling between source and preview, we need to establish a mapping between source lines and
 * coordinates of their presentation.
 *
 * For simplicity, let's suppose that the source code is immutable. [MarkdownProcessor] parses it and yields a list of
 * [MarkdownBlock]s. Unfortunately, it doesn't contain any information about the source lines, as the need to keep them
 * and reserve more heap is not strong enough. (The hypothesis is that most users just need to read the .md file and not
 * to edit it)
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
 * # Identity
 *
 * Some supporting structures could require having hashmaps with [MarkdownBlock]s as keys. However, many
 * [MarkdownBlock]s are data classes, and their hashCode are based on their contents. For example, two
 * [MarkdownBlock.Paragraph]s are equal if their texts are equal. If there are equal blocks in the preview, layout data
 * for one of them may overwrite the data for another one, causing the synchronizer to scroll unpredictably.
 *
 * To address this issue, use [LocatableMarkdownBlock]. It decorates the regular [MarkdownBlock] with the information
 * about the source lines it spans over. If it so happens that two [MarkdownBlock]s are equal, their decorations are
 * equal only if their source lines are somehow the same, which doesn't make any difference to the synchronizer.
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
 * @see [LocatableMarkdownBlock]
 * @see [PerLine]
 */
@ApiStatus.Experimental
@ExperimentalJewelApi
public abstract class ScrollingSynchronizer {
    /** Scroll the preview to the position that match the given [sourceLine] the best. */
    public abstract suspend fun scrollToLine(sourceLine: Int, animationSpec: AnimationSpec<Float> = SpringSpec())

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
     *
     * Return [MarkdownBlock] processed by the synchronizer. For example, see [PerLine.acceptBlockSpans] which just
     * wraps a given [block] to a unique class, to separate equal blocks located in different places in the document.
     */
    public abstract fun acceptBlockSpans(block: MarkdownBlock, sourceRange: IntRange): MarkdownBlock

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

    /**
     * A decorator over [MarkdownBlock] that holds information about the source lines it spans over.
     *
     * This is needed to support scrolling within code blocks, as they don't have their own global layout.
     *
     * @property originalBlock the original block that was processed by [MarkdownProcessor]
     * @property lines the source lines this block spans over
     * @see [acceptBlockSpans]
     * @see [acceptGlobalPosition]
     * @see [acceptTextLayout]
     */
    public class LocatableMarkdownBlock(public val originalBlock: MarkdownBlock, public val lines: IntRange) :
        MarkdownBlock.CustomBlock {
        override fun equals(other: Any?): Boolean =
            other is LocatableMarkdownBlock && originalBlock == other.originalBlock && lines == other.lines

        override fun hashCode(): Int = originalBlock.hashCode() * 31 + lines.hashCode() * 31

        override fun toString(): String = "LocatableMarkdownBlock(originalBlock=$originalBlock, lines=$lines)"
    }

    private class PerLine(private val scrollState: ScrollState) : ScrollingSynchronizer() {
        private val lines2Blocks = TreeMap<Int, MarkdownBlock>()
        private val blocks2Top = mutableMapOf<MarkdownBlock, Int>()
        private val previousPositions = mutableMapOf<MarkdownBlock, Int>()

        private var lastBlocks = emptyList<LocatableMarkdownBlock>()
        private val currentBlocks = mutableListOf<LocatableMarkdownBlock>()

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

        override suspend fun scrollToLine(sourceLine: Int, animationSpec: AnimationSpec<Float>) {
            val block = findBestBlockForLine(sourceLine) ?: return
            val y = blocks2Top[block] ?: return
            if (y < 0) return
            val lineRange = (block as? LocatableMarkdownBlock)?.lines ?: return
            val textOffsets = blocks2TextOffsets[block]
            // The line may be empty and represent no block,
            // in this case scroll to the first line of the first block positioned after the line
            val lineIndexInBlock = maxOf(0, sourceLine - lineRange.first)
            val lineOffset = textOffsets?.get(lineIndexInBlock) ?: 0
            scrollState.animateScrollTo(y + lineOffset, animationSpec)
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
        }

        /**
         * Update the internal structures based on the difference between the blocks before and after the Markdown
         * source is edited.
         */
        override fun afterProcessing() {
            var firstChangedIndex = -1
            // First, find the "common prefix" before and after changes, in terms of topmost Markdown blocks whose
            // contents didn't change.
            // Their source lines might have changed, though; make sure all the internal maps keep the most recent
            // blocks as keys.
            for (i in 0..minOf(lastBlocks.lastIndex, currentBlocks.lastIndex)) {
                val current = currentBlocks[i]
                val last = lastBlocks[i]
                if (!current.originallyEquals(last)) {
                    firstChangedIndex = i
                    break
                }
                if (current.lines != last.lines) {
                    replace(last, current)
                }
            }
            // Second, find the "common suffix". Here, we don't need to update all the structures,
            // as some of them will be updated automatically during recomposition.
            // Clean up those structures to get rid of outdated keys afterwards.
            for (lastI in lastBlocks.lastIndex downTo firstChangedIndex + 1) {
                val currI = currentBlocks.lastIndex - (lastBlocks.lastIndex - lastI)
                if (currI < 0) break
                val current = currentBlocks[currI]
                val last = lastBlocks[lastI]
                if (!current.originallyEquals(last)) {
                    break
                }
                // compose does not recalculate text layout
                // on unchanged code blocks positioned after the changed part,
                // so relevant keys in blocks2TextOffsets have to be updated manually
                if (current.originalBlock is MarkdownBlock.CodeBlock) {
                    blocks2TextOffsets.replaceKey(last, current)
                }
            }
            if (firstChangedIndex >= 0) {
                for (i in firstChangedIndex..lastBlocks.lastIndex) {
                    blocks2Top.remove(lastBlocks[i])
                    previousPositions.remove(lastBlocks[i])
                }
            }
            lastBlocks = ArrayList(currentBlocks)
            currentBlocks.clear()
        }

        // literally equals check that bypasses absolute line numbers;
        // here I rely on the fact that a block cannot have its own contents and children at the
        // same time
        // (or, at least, its contents always take a fixed number of lines)
        private fun MarkdownBlock.originallyEquals(other: MarkdownBlock): Boolean {
            if (
                this is LocatableMarkdownBlock &&
                    other is LocatableMarkdownBlock &&
                    lines.last - lines.first != other.lines.last - other.lines.first
            ) {
                return false
            }
            val originalBlock = (this as? LocatableMarkdownBlock)?.originalBlock ?: this
            val otherOriginalBlock = (other as? LocatableMarkdownBlock)?.originalBlock ?: other
            if (originalBlock !is WithChildBlocks || otherOriginalBlock !is WithChildBlocks) {
                return originalBlock == otherOriginalBlock
            }

            if (originalBlock.children.size != otherOriginalBlock.children.size) return false
            return originalBlock.children.zip(otherOriginalBlock.children).all { (a, b) -> a.originallyEquals(b) }
        }

        private fun <K, V> MutableMap<K, V>.replaceKey(oldKey: K, newKey: K) {
            remove(oldKey)?.let { put(newKey, it) }
        }

        private fun replace(oldBlock: MarkdownBlock, newBlock: MarkdownBlock) {
            blocks2Top.replaceKey(oldBlock, newBlock)
            blocks2TextOffsets.replaceKey(oldBlock, newBlock)
            previousPositions.replaceKey(oldBlock, newBlock)
        }

        override fun acceptBlockSpans(block: MarkdownBlock, sourceRange: IntRange): MarkdownBlock {
            val locatableMarkdownBlock = LocatableMarkdownBlock(block, sourceRange)
            for (line in sourceRange) {
                // DFS -- keep the innermost block for the given line
                lines2Blocks.putIfAbsent(line, locatableMarkdownBlock)
            }
            currentBlocks += locatableMarkdownBlock
            return locatableMarkdownBlock
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
            val originalBlock = (block as? LocatableMarkdownBlock)?.originalBlock ?: return
            if (originalBlock !is MarkdownBlock.CodeBlock) return
            val sourceLines = block.lines

            var y = 0
            val list = mutableListOf<Int>()

            if (originalBlock is MarkdownBlock.CodeBlock.FencedCodeBlock) {
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
            } else if (originalBlock is MarkdownBlock.CodeBlock.IndentedCodeBlock) {
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
