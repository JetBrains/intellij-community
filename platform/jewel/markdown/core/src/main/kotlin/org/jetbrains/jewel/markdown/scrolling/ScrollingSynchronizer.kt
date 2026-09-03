// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the
// Apache 2.0 license.
package org.jetbrains.jewel.markdown.scrolling

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.TextLayoutResult
import java.util.TreeMap
import kotlin.math.roundToInt
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
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
 * # Continuous scrolling
 *
 * [scrollToLine] moves the preview from block to block, so it stands still while the editor scrolls through a long,
 * soft-wrapped line and then jumps. [ContinuousScrollingSynchronizer] instead interpolates between the tops of the
 * blocks around the current offset, in either direction, given where the editor puts each source line. [syncScrolling]
 * uses it to keep an editor and a preview in step.
 *
 * # Editing
 *
 * [MarkdownProcessor] always yields all the blocks that are present in the source, even in optimized mode, so
 * [acceptBlockSpans] is not really affected by editing. [acceptGlobalPosition] is trickier, as it is not triggered on
 * blocks preceding the change. [acceptTextLayout] is even more intricate, as it may or may not be triggered on blocks
 * following the change. It implies that mappings should be adjusted accordingly. [beforeProcessing] and
 * [afterProcessing] can help with that, as they're invoked before and after every re-parse, i.e. every change in the
 * file. See [PerLine] as one of the possible implementations, with [PerLineScrolled] covering a [ScrollState] preview
 * and [PerLineLazy] a [LazyListState] one.
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
    /**
     * Scroll the preview to the position that matches the given [sourceLine] the best.
     *
     * Don't extend this function, implement [scrollToCoordinate] and [findYCoordinateToScroll] instead. `open` is
     * preserved for compatibility reasons and will be removed in the future.
     */
    @ApiStatus.NonExtendable
    public open suspend fun scrollToLine(sourceLine: Int, animationSpec: AnimationSpec<Float> = SpringSpec()) {
        scrollToCoordinate(findYCoordinateToScroll(sourceLine), animationSpec)
    }

    /** Scroll the preview to the given vertical position [y] using given [animationSpec]. */
    protected abstract suspend fun scrollToCoordinate(y: Int, animationSpec: AnimationSpec<Float> = SpringSpec())

    /** Find the vertical position in the preview that matches the given [sourceLine] the best. */
    protected abstract suspend fun findYCoordinateToScroll(sourceLine: Int): Int

    /**
     * Called when [MarkdownProcessor] processes the raw Markdown text. The processing itself is passed as an [action].
     */
    public fun <T> process(action: () -> T): T {
        beforeProcessing()
        return try {
            action()
        } finally {
            afterProcessing()
        }
    }

    /** Called before [MarkdownProcessor] starts processing the raw Markdown text. */
    protected abstract fun beforeProcessing()

    /** Called after [MarkdownProcessor] starts processing the raw Markdown text. */
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

    /**
     * Accept the [coordinates] of the scrolled content holding all the blocks. Block positions arrive relative to the
     * composition root; this is what makes them relative to the preview when the preview doesn't start at the root.
     * Called on first composition and whenever the content moves, including on every scroll.
     */
    public open fun acceptContentPosition(coordinates: LayoutCoordinates) {}

    /**
     * Accept the blocks rendered as the items of a lazy preview, so that their positions in the list can be used as
     * item indices. Called by [LazyMarkdown][org.jetbrains.jewel.markdown.LazyMarkdown] whenever the list changes; a
     * preview that is not a lazy list never calls this.
     *
     * @param blocks The blocks rendered as lazy items, in the order they appear in the list.
     */
    public open fun acceptItemBlocks(blocks: List<MarkdownBlock>) {}

    /** Companion object for [ScrollingSynchronizer]. */
    public companion object {
        /**
         * Creates a [ContinuousScrollingSynchronizer] for the given [scrollState], or `null` if the scroll state type
         * is not supported. Unlike [scrollToLine], it can follow the editor continuously, through [syncScrolling].
         *
         * Use [LazyListState] with [LazyMarkdown][org.jetbrains.jewel.markdown.LazyMarkdown], and [ScrollState] with
         * [Markdown][org.jetbrains.jewel.markdown.Markdown].
         *
         * @param scrollState The scroll state of the preview to synchronize.
         */
        public fun createContinuous(scrollState: ScrollableState): ContinuousScrollingSynchronizer? =
            when (scrollState) {
                is ScrollState -> PerLineScrolled(scrollState)
                is LazyListState -> PerLineLazy(scrollState)
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

    /**
     * Maps source lines onto preview positions, block by block. What a "preview position" is depends on how the preview
     * scrolls, which is all the subclasses below have to say.
     */
    private abstract class PerLine : ContinuousScrollingSynchronizer() {
        private val lines2Blocks = TreeMap<Int, MarkdownBlock>()
        protected val blocks2Top: MutableMap<MarkdownBlock, Int> = mutableMapOf()
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

        // Parsing mutates the maps on whichever thread parses, while positions and lookups come from the UI thread.
        protected val lock: Any = Any()

        // Like block positions: measured from the root plus the scroll offset, so it doesn't change while scrolling.
        protected var contentTop: Int = 0
            private set

        /** How far the preview has been scrolled, for turning viewport-relative positions into stable ones. */
        protected open val previewScrollOffset: Int
            get() = 0

        /**
         * A stretch across which the editor moves from [editorFrom] to [editorTo] while the preview moves from
         * [previewFrom] to [previewTo], linearly, which is what makes the two directions exact inverses.
         *
         * A preview coordinate is an absolute scroll offset for [PerLineScrolled] and a distance into one lazy item for
         * [PerLineLazy]. Finding the stretch around a position is theirs; mapping within it is not.
         */
        protected class Stretch(val editorFrom: Int, val editorTo: Int, val previewFrom: Int, val previewTo: Int) {
            fun previewOffsetAt(editorOffset: Int): Int =
                previewFrom + ((previewTo - previewFrom) * fraction(editorOffset, editorFrom, editorTo)).roundToInt()

            fun editorOffsetAt(previewOffset: Int): Int =
                editorFrom + ((editorTo - editorFrom) * fraction(previewOffset, previewFrom, previewTo)).roundToInt()

            /** A stretch with nowhere to go sits at its start. */
            private fun fraction(value: Int, from: Int, to: Int): Float =
                if (to <= from) 0f else ((value - from).toFloat() / (to - from)).coerceIn(0f, 1f)
        }

        override suspend fun findYCoordinateToScroll(sourceLine: Int): Int =
            synchronized(lock) {
                blocksSortedByPreference(sourceLine).firstNotNullOfOrNull { it.positionToScroll(sourceLine) } ?: 0
            }

        override fun acceptContentPosition(coordinates: LayoutCoordinates) {
            contentTop = coordinates.positionInRoot().y.toInt() + previewScrollOffset
        }

        private fun blocksSortedByPreference(sourceLine: Int) = sequence {
            val blockOnLine = lines2Blocks[sourceLine]
            if (blockOnLine != null) {
                yield(blockOnLine)
            } else {
                // If there is no block that covers the line,
                // the next best block is the one **after** the line.
                // Otherwise, when scrolling down the source,
                // on empty lines the preview will scroll
                // in the opposite direction
                val firstBlockAfterLine = lines2Blocks.higherEntry(sourceLine)?.value
                if (firstBlockAfterLine != null) {
                    yield(firstBlockAfterLine)
                }
            }
            // Otherwise, look for the closest block positioned before the line.
            // This way, the corresponding preview line will be located
            // below the viewport's top point (and the user still has a chance
            // to see it in the visible area)
            val blocksBeforeLine = lines2Blocks.headMap(sourceLine)
            val blocksBeforeLineClosestFirst = blocksBeforeLine.values.reversed()
            for (block in blocksBeforeLineClosestFirst) {
                yield(block)
            }
        }

        private fun MarkdownBlock.positionToScroll(sourceLine: Int): Int? {
            val y = (blocks2Top[this] ?: return null) - contentTop
            return y + offsetOfLineWithin(sourceLine)
        }

        /** Where [sourceLine] starts inside this block, for blocks whose lines map 1:1 onto the preview. */
        protected fun MarkdownBlock.offsetOfLineWithin(sourceLine: Int): Int {
            // The line may be empty and represent no block,
            // in this case scroll to the first line of the first block positioned after the line
            val lineRange = (this as? LocatableMarkdownBlock)?.lines ?: return 0
            val lineIndexInBlock = maxOf(0, sourceLine - lineRange.first)
            return synchronized(lock) { blocks2TextOffsets[this]?.getOrNull(lineIndexInBlock) } ?: 0
        }

        override fun beforeProcessing() {
            // acceptBlockSpans works on ALL the nodes, including those unchanged,
            // so it will be fully rebuilt during processing anyway
            synchronized(lock) { lines2Blocks.clear() }
        }

        /**
         * Update the internal structures based on the difference between the blocks before and after the Markdown
         * source is edited.
         */
        override fun afterProcessing(): Unit = synchronized(lock) { reconcileBlocks() }

        private fun reconcileBlocks() {
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
            val locatableMarkdownBlock = block as? LocatableMarkdownBlock ?: LocatableMarkdownBlock(block, sourceRange)
            synchronized(lock) {
                for (line in sourceRange) {
                    // DFS -- keep the innermost block for the given line
                    lines2Blocks.putIfAbsent(line, locatableMarkdownBlock)
                }
                currentBlocks += locatableMarkdownBlock
            }
            return locatableMarkdownBlock
        }

        override fun acceptGlobalPosition(block: MarkdownBlock, coordinates: LayoutCoordinates) {
            // coordinates are relative to the current viewport
            // (which also means onPositionedGlobally is triggered when scrolling);
            // to get the real absolute coordinates we need to consider scroll state
            val y = coordinates.positionInRoot().y.toInt() + previewScrollOffset

            synchronized(lock) {
                // let's not recalculate internal structures on the preview scrolling -- more safety
                val oldY = previousPositions[block]
                if (oldY == null || y != oldY) {
                    blocks2Top[block] = y
                    previousPositions[block] = y
                }
            }
        }

        override fun acceptTextLayout(block: MarkdownBlock, textLayout: TextLayoutResult) {
            val originalBlock = (block as? LocatableMarkdownBlock)?.originalBlock ?: return
            if (originalBlock !is MarkdownBlock.CodeBlock) return
            synchronized(lock) { blocks2TextOffsets[block] = textOffsets(originalBlock, block.lines, textLayout) }
        }

        private fun textOffsets(
            originalBlock: MarkdownBlock.CodeBlock,
            sourceLines: IntRange,
            textLayout: TextLayoutResult,
        ): List<Int> {
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
                // which means all the lines in the range [first line + 1; last line - 1]
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
            return list
        }
    }

    /**
     * A preview that scrolls as one column. Block positions and scroll offsets live in the same absolute space, so a
     * source line maps onto a pixel offset and the two panes can be interpolated against each other directly.
     */
    private class PerLineScrolled(private val scrollState: ScrollState) : PerLine() {
        override val previewScrollOffset: Int
            get() = scrollState.value

        override suspend fun scrollToCoordinate(y: Int, animationSpec: AnimationSpec<Float>) {
            scrollState.animateScrollTo(y, animationSpec)
        }

        override suspend fun scrollPreviewTo(
            editorOffset: Int,
            editorMaxOffset: Int,
            editorOffsetOfLine: (Int) -> Int?,
        ) {
            val stretch = anchors(editorMaxOffset, editorOffsetOfLine).stretchAround(editorOffset) { it.editorOffset }
            scrollState.scrollTo(stretch.previewOffsetAt(editorOffset))
        }

        override fun editorOffsetAtPreview(editorMaxOffset: Int, editorOffsetOfLine: (Int) -> Int?): Int {
            val previewOffset = scrollState.value
            val stretch = anchors(editorMaxOffset, editorOffsetOfLine).stretchAround(previewOffset) { it.previewOffset }
            return stretch.editorOffsetAt(previewOffset)
        }

        /** The top of one block, as an editor scroll offset and as a preview scroll offset. */
        private class Anchor(val editorOffset: Int, val previewOffset: Int)

        /** One anchor per positioned block, sorted by editor offset, between both panes' zero and maximum offsets. */
        private fun anchors(editorMaxOffset: Int, editorOffsetOfLine: (Int) -> Int?): List<Anchor> {
            val previewMaxOffset = scrollState.maxValue
            val blockAnchors =
                synchronized(lock) {
                    blocks2Top.mapNotNull { (block, top) ->
                        val line = (block as? LocatableMarkdownBlock)?.lines?.first ?: return@mapNotNull null
                        val editorOffset = editorOffsetOfLine(line) ?: return@mapNotNull null
                        Anchor(editorOffset, top - contentTop)
                    }
                }
            // A pane's maximum offset is Int.MAX_VALUE until its first measure pass. Pairing the two ends then would
            // map offsets onto ones the other pane can't hold, and ScrollState overflows to -1 when it scrolls back
            // down from Int.MAX_VALUE. Until both ends are known, the blocks are all we have to go on.
            val bothEndsKnown = editorMaxOffset != Int.MAX_VALUE && previewMaxOffset != Int.MAX_VALUE
            val end = if (bothEndsKnown) listOf(Anchor(editorMaxOffset, previewMaxOffset)) else emptyList()
            return blockAnchors
                // Blocks on either edge would only add a jump: the first block sits below the panes' padding, and
                // blocks in the last screenful never reach the top of a pane.
                .filter { it.editorOffset in 1..<editorMaxOffset && it.previewOffset in 1..<previewMaxOffset }
                .sortedWith(compareBy({ it.editorOffset }, { it.previewOffset }))
                .let { listOf(Anchor(0, 0)) + it + end }
        }

        /**
         * The stretch between the two anchors bracketing [value] in the space [key] reads. Outside the list it
         * degenerates onto the nearest end, which is what pins the preview there.
         */
        private fun List<Anchor>.stretchAround(value: Int, key: (Anchor) -> Int): Stretch {
            val hi = -(binarySearch { if (key(it) < value) -1 else 1 } + 1)
            val (lo, high) =
                when {
                    hi == size -> last() to last()
                    hi == 0 -> first() to first()
                    else -> this[hi - 1] to this[hi]
                }
            return Stretch(lo.editorOffset, high.editorOffset, lo.previewOffset, high.previewOffset)
        }
    }

    /**
     * A preview that scrolls as a lazy list. There is no absolute scroll offset to interpolate against: only composed
     * items have a position at all, and the ones scrolled past are disposed. So the preview's position is described the
     * way the list itself describes it -- which item is at the top, and how far into it -- and a source line is located
     * by the item holding it rather than by a pixel coordinate.
     */
    private class PerLineLazy(private val state: LazyListState) : PerLine() {
        // The blocks LazyMarkdown renders as items: a block's position in this list is its item index
        private var itemBlocks = emptyList<MarkdownBlock>()

        override fun acceptItemBlocks(blocks: List<MarkdownBlock>) {
            itemBlocks = blocks
        }

        override suspend fun scrollToCoordinate(y: Int, animationSpec: AnimationSpec<Float>) {
            state.animateScrollBy(y.toFloat(), animationSpec)
        }

        override suspend fun findYCoordinateToScroll(sourceLine: Int): Int {
            // An item that has never been composed has no coordinate to find, so put the one holding this line at the
            // top of the viewport first. What is left to scroll is the line's own offset inside the block, if it has
            // one, which is what scrollToCoordinate then animates.
            val index = itemIndexOfLine(sourceLine) ?: return 0
            state.scrollToItem(index)
            return itemBlocks[index].offsetOfLineWithin(sourceLine)
        }

        override suspend fun scrollPreviewTo(
            editorOffset: Int,
            editorMaxOffset: Int,
            editorOffsetOfLine: (Int) -> Int?,
        ) {
            if (editorMaxOffset in 1..editorOffset && itemBlocks.isNotEmpty()) {
                // The editor has nowhere left to go, so neither has the preview; the last item may be taller than it
                state.scrollToItem(itemBlocks.lastIndex)
                state.scrollBy(remainingScroll()?.toFloat() ?: 0f)
                return
            }
            val index = itemAt(editorOffset, editorMaxOffset, editorOffsetOfLine) ?: return
            // An item that isn't on screen has no height yet: land on its top first, so it can be measured
            if (heightOfItem(index) <= 0) state.scrollToItem(index)
            val stretch = stretchAt(index, editorMaxOffset, editorOffsetOfLine)
            state.scrollToItem(index, stretch?.previewOffsetAt(editorOffset) ?: 0)
        }

        override fun editorOffsetAtPreview(editorMaxOffset: Int, editorOffsetOfLine: (Int) -> Int?): Int {
            if (editorMaxOffset != Int.MAX_VALUE && state.canScrollBackward && !state.canScrollForward) {
                // The preview is at its end, and has one to be at, so the editor belongs at its own
                return editorMaxOffset
            }
            val stretch = stretchAt(state.firstVisibleItemIndex, editorMaxOffset, editorOffsetOfLine) ?: return 0
            return stretch.editorOffsetAt(state.firstVisibleItemScrollOffset)
        }

        /**
         * The stretch the editor crosses while the preview scrolls through item [index]: from that item's own editor
         * offset to the next item's top, across the item's height. With no next top reachable it runs to the editor's
         * end instead, across whatever the list has left to scroll; with no known editor end either, nowhere at all.
         *
         * `null` if there is no such item, or if the editor doesn't know the line it starts on.
         */
        private fun stretchAt(index: Int, editorMaxOffset: Int, editorOffsetOfLine: (Int) -> Int?): Stretch? {
            val from = editorOffsetOfItem(index, editorOffsetOfLine) ?: return null
            val nextTop = nextItemTop(index, editorMaxOffset, editorOffsetOfLine)
            if (nextTop != null) return Stretch(from, nextTop, 0, heightOfItem(index))
            if (editorMaxOffset == Int.MAX_VALUE) return Stretch(from, from, 0, 0)
            val toListEnd = (distanceToListEnd(index) ?: heightOfItem(index)).coerceAtLeast(0)
            return Stretch(from, editorMaxOffset, 0, toListEnd)
        }

        /** The item to put at the top of the preview for [editorOffset]: the last one starting at or before it. */
        private fun itemAt(editorOffset: Int, editorMaxOffset: Int, editorOffsetOfLine: (Int) -> Int?): Int? =
            // A scan per scroll, one entry per top-level block: the same PerLineScrolled pays to build its anchors
            itemBlocks.indices.lastOrNull { index ->
                val offset = editorOffsetOfItem(index, editorOffsetOfLine)
                offset != null && offset <= editorOffset && offset < editorMaxOffset
            }

        /**
         * The editor scroll offset of the next item's top, or `null` when a pane runs out of scroll before that top is
         * reached: the stretch then ends at both panes' ends, as [PerLineScrolled] pairs their maximum offsets.
         */
        private fun nextItemTop(index: Int, editorMaxOffset: Int, editorOffsetOfLine: (Int) -> Int?): Int? {
            val from = editorOffsetOfItem(index, editorOffsetOfLine) ?: return null
            val next = editorOffsetOfItem(index + 1, editorOffsetOfLine) ?: return null
            if (next <= from || next >= editorMaxOffset) return null
            val toListEnd = distanceToListEnd(index) ?: return next
            return next.takeIf { heightOfItem(index) <= toListEnd }
        }

        /**
         * How far the list can still scroll from the top of item [index], or `null` if that item or the list's end
         * isn't on screen: callers then measure the last stretch against the item's own height instead, until the end
         * comes into view.
         */
        private fun distanceToListEnd(index: Int): Int? {
            val visibleItems = state.layoutInfo.visibleItemsInfo
            val item = visibleItems.firstOrNull { it.index == index } ?: return null
            val scrolledPastTop = state.firstVisibleItemScrollOffset + visibleItems.first().offset - item.offset
            return remainingScroll()?.plus(scrolledPastTop)
        }

        /** How far the list can still scroll forward, or `null` while its last item has never been composed. */
        private fun remainingScroll(): Int? {
            val layout = state.layoutInfo
            val last = layout.visibleItemsInfo.lastOrNull() ?: return null
            if (last.index != layout.totalItemsCount - 1) return null
            // At the end of the list the last item's bottom sits at the bottom of the content area
            return (last.offset + last.size - layout.viewportEndOffset + layout.afterContentPadding).coerceAtLeast(0)
        }

        /** The editor scroll offset at which item [index] starts, or `null` if there is no such item or line. */
        private fun editorOffsetOfItem(index: Int, editorOffsetOfLine: (Int) -> Int?): Int? {
            val block = itemBlocks.getOrNull(index) as? LocatableMarkdownBlock ?: return null
            return editorOffsetOfLine(block.lines.first)
        }

        private fun heightOfItem(index: Int): Int =
            state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }?.size ?: 0

        /** The item holding [sourceLine], or the first one after it, or `null` if there is no such item. */
        private fun itemIndexOfLine(sourceLine: Int): Int? =
            itemBlocks
                .indexOfFirst { block ->
                    val lines = (block as? LocatableMarkdownBlock)?.lines
                    lines != null && sourceLine <= lines.last
                }
                .takeIf { it >= 0 }
    }
}
