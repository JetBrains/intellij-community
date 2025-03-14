package org.jetbrains.jewel.markdown.processing

import org.commonmark.node.Block
import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.CustomBlock
import org.commonmark.node.Document
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.Heading
import org.commonmark.node.HtmlBlock
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.ListBlock as CMListBlock
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.SourceSpan
import org.commonmark.node.ThematicBreak
import org.commonmark.parser.Parser
import org.intellij.lang.annotations.Language
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.InternalJewelApi
import org.jetbrains.jewel.foundation.code.MimeType
import org.jetbrains.jewel.markdown.InlineMarkdown
import org.jetbrains.jewel.markdown.MarkdownBlock
import org.jetbrains.jewel.markdown.MarkdownBlock.CodeBlock
import org.jetbrains.jewel.markdown.MarkdownBlock.ListBlock
import org.jetbrains.jewel.markdown.MarkdownMode
import org.jetbrains.jewel.markdown.extensions.MarkdownBlockProcessorExtension
import org.jetbrains.jewel.markdown.extensions.MarkdownDelimitedInlineProcessorExtension
import org.jetbrains.jewel.markdown.extensions.MarkdownProcessorExtension
import org.jetbrains.jewel.markdown.rendering.DefaultInlineMarkdownRenderer
import org.jetbrains.jewel.markdown.scrolling.ScrollingSynchronizer

/**
 * Reads raw Markdown strings and processes them into a list of [MarkdownBlock].
 *
 * @param extensions Extensions to use when processing the Markdown (e.g., to support parsing custom block-level
 *   Markdown).
 * @param markdownMode Indicates a scenario of how the file is going to be presented. Default is
 *   [MarkdownMode.Standalone]; set this to [MarkdownMode.EditorPreview] if this parser will be used in an editor
 *   scenario, where the raw Markdown is only ever going to change slightly but frequently (e.g., as the user types).
 *   This means it will only update the changed blocks by keeping state in memory.
 *
 *   You can also pass a [ScrollingSynchronizer] to [MarkdownMode.EditorPreview] to enable auto-scrolling in the preview
 *   according to the position in the editor.
 *
 *   **Attention:** do **not** reuse or share an instance of [MarkdownProcessor] if [markdownMode] is
 *   [MarkdownMode.EditorPreview]. Processing entirely different Markdown strings will defeat the purpose of the
 *   optimization. When in editor mode, the instance of [MarkdownProcessor] is **not** thread-safe!
 *
 * @param commonMarkParser The CommonMark [Parser] used to parse the Markdown. By default it's a vanilla instance
 *   provided by the [MarkdownParserFactory], but you can provide your own if you need to customize the parser — e.g.,
 *   to ignore certain tags. If [markdownMode] is `MarkdownMode.WithEditor`, make sure you set
 *   `includeSourceSpans(IncludeSourceSpans.BLOCKS)` on the parser.
 */
@ExperimentalJewelApi
public class MarkdownProcessor(
    public val extensions: List<MarkdownProcessorExtension> = emptyList(),
    private val markdownMode: MarkdownMode = MarkdownMode.Standalone,
    private val commonMarkParser: Parser =
        MarkdownParserFactory.create(optimizeEdits = markdownMode is MarkdownMode.EditorPreview, extensions),
) {
    /** The [block-level processor extensions][MarkdownBlockProcessorExtension]s used by this processor. */
    public val blockExtensions: List<MarkdownBlockProcessorExtension> =
        extensions.mapNotNull { it.blockProcessorExtension }

    /**
     * The [delimited inline node processor extensions][MarkdownDelimitedInlineProcessorExtension]s used by this
     * processor.
     */
    public val delimitedInlineExtensions: List<MarkdownDelimitedInlineProcessorExtension> =
        extensions.mapNotNull { it.delimitedInlineProcessorExtension }

    private var currentState = State("", emptyList())

    @TestOnly
    internal fun getCurrentIndexesInTest() = buildList {
        for (block in currentState.blocks) {
            block.traverseAll { node -> add(node.sourceSpans) }
        }
    }

    private val scrollingSynchronizer: ScrollingSynchronizer? =
        (markdownMode as? MarkdownMode.EditorPreview)?.scrollingSynchronizer

    /**
     * Parses a Markdown document, translating from CommonMark 0.31.2 to a list of [MarkdownBlock]. Inline Markdown in
     * leaf nodes is contained in [InlineMarkdown], which can be rendered to an
     * [androidx.compose.ui.text.AnnotatedString] by using [DefaultInlineMarkdownRenderer.renderAsAnnotatedString].
     *
     * @param rawMarkdown the raw Markdown string to process.
     * @see DefaultInlineMarkdownRenderer
     */
    public fun processMarkdownDocument(@Language("Markdown") rawMarkdown: String): List<MarkdownBlock> {
        if (scrollingSynchronizer == null) {
            return doProcess(rawMarkdown)
        }
        return scrollingSynchronizer.process { doProcess(rawMarkdown) }
    }

    private fun doProcess(rawMarkdown: String): List<MarkdownBlock> {
        val blocks =
            if (markdownMode is MarkdownMode.EditorPreview) {
                processWithQuickEdits(rawMarkdown)
            } else {
                parseRawMarkdown(rawMarkdown)
            }

        return blocks.mapNotNull { child -> child.tryProcessMarkdownBlock() }
    }

    @VisibleForTesting
    internal fun processWithQuickEdits(@Language("Markdown") rawMarkdown: String): List<Block> {
        val (previousText, previousBlocks) = currentState
        if (previousText == rawMarkdown) return previousBlocks
        // make sure we have at least one element
        if (previousBlocks.isEmpty()) {
            val newBlocks = parseRawMarkdown(rawMarkdown)
            currentState = State(rawMarkdown, newBlocks)
            return newBlocks
        }
        val blocksInfo = findChangedBlocks(previousText, rawMarkdown, previousBlocks)
        val firstBlock = blocksInfo.firstBlock
        val blockAfterLast = blocksInfo.blockAfterLast
        val updatedBlocks = parseRawMarkdown(blocksInfo.updatedText)
        val firstBlockOffset = previousBlocks[firstBlock].sourceSpans.first()
        // sourceSpans in updatedBlocks start from line index 0, not from the actual line
        //      the update part starts in the document;
        for (block in updatedBlocks) {
            block.traverseAll { node ->
                node.sourceSpans =
                    node.sourceSpans.map { span ->
                        SourceSpan.of(
                            span.lineIndex + firstBlockOffset.lineIndex,
                            span.columnIndex,
                            span.inputIndex + firstBlockOffset.inputIndex,
                            span.length,
                        )
                    }
            }
        }
        val nCharsDelta = rawMarkdown.length - previousText.length
        val suffixBlocks =
            if (blockAfterLast == -1) emptyList() else previousBlocks.subList(blockAfterLast, previousBlocks.size)
        // Addressing the remaining blocks which shift by lines delta between new and old text.
        for (block in suffixBlocks) {
            block.traverseAll { node ->
                node.sourceSpans =
                    node.sourceSpans.map { span ->
                        SourceSpan.of(
                            span.lineIndex + blocksInfo.nLinesDelta,
                            span.columnIndex,
                            span.inputIndex + nCharsDelta,
                            span.length,
                        )
                    }
            }
        }

        val newBlocks = previousBlocks.subList(0, firstBlock) + updatedBlocks + suffixBlocks
        currentState = State(rawMarkdown, newBlocks)

        return newBlocks
    }

    @VisibleForTesting
    internal fun findChangedBlocks(
        previousText: String,
        updatedText: String,
        previousBlocks: List<Block>,
    ): FindChangedBlocksResult {
        val nCharsDelta = updatedText.length - previousText.length
        val commonPrefix = previousText.commonPrefixWith(updatedText)
        val prefixPos = commonPrefix.length
        // remove prefixes to avoid overlap
        val commonSuffix =
            previousText.removePrefix(commonPrefix).commonSuffixWith(updatedText.removePrefix(commonPrefix))
        val suffixPos = previousText.length - commonSuffix.length
        val nLinesDelta =
            updatedText.subSequence(prefixPos, updatedText.length - commonSuffix.length).lineSequence().count() -
                previousText.subSequence(prefixPos, suffixPos).lineSequence().count()
        val previousIndexes = previousBlocks.map { block -> block.sourceSpans.first().inputIndex }
        // if modification starts at the edge, include previous by using less instead of less equal
        val firstBlock = previousIndexes.indexOfLast { it < prefixPos }.takeIf { it != -1 } ?: 0
        val blockAfterLast = previousIndexes.indexOfFirst { it > suffixPos }
        val updatedText =
            updatedText.substring(
                previousIndexes[firstBlock],
                if (blockAfterLast == -1) updatedText.length else previousIndexes[blockAfterLast] - 1 + nCharsDelta,
            )
        return FindChangedBlocksResult(firstBlock, blockAfterLast, updatedText, nLinesDelta)
    }

    internal data class FindChangedBlocksResult(
        val firstBlock: Int,
        val blockAfterLast: Int,
        val updatedText: String,
        val nLinesDelta: Int,
    )

    private fun parseRawMarkdown(@Language("Markdown") rawMarkdown: String): List<Block> {
        val document =
            commonMarkParser.parse(rawMarkdown) as? Document ?: error("This doesn't look like a Markdown document")

        return buildList { document.forEachChild { child -> if (child is Block) add(child) } }
    }

    private fun Node.tryProcessMarkdownBlock(): MarkdownBlock? =
        // Non-Block children are ignored
        when (this) {
            is Paragraph -> toMarkdownParagraph()
            is Heading -> toMarkdownHeadingOrNull()
            is BulletList -> toMarkdownListOrNull()
            is OrderedList -> toMarkdownListOrNull()
            is BlockQuote -> toMarkdownBlockQuote()
            is FencedCodeBlock -> toMarkdownCodeBlockOrNull()
            is IndentedCodeBlock -> toMarkdownCodeBlockOrNull()
            is ThematicBreak -> MarkdownBlock.ThematicBreak
            is HtmlBlock -> toMarkdownHtmlBlockOrNull()
            is CustomBlock -> {
                blockExtensions.find { it.canProcess(this) }?.processMarkdownBlock(this, this@MarkdownProcessor)
            }

            else -> null
        }.let { block ->
            if (scrollingSynchronizer != null && this is Block && block != null) {
                postProcess(scrollingSynchronizer, this, block)
            } else {
                block
            }
        }

    private fun postProcess(
        scrollingSynchronizer: ScrollingSynchronizer,
        block: Block,
        mdBlock: MarkdownBlock,
    ): MarkdownBlock {
        val spans = block.sourceSpans.takeIf { it.isNotEmpty() } ?: return mdBlock
        return scrollingSynchronizer.acceptBlockSpans(mdBlock, spans.first().lineIndex..spans.last().lineIndex)
    }

    private fun Paragraph.toMarkdownParagraph(): MarkdownBlock.Paragraph =
        MarkdownBlock.Paragraph(readInlineMarkdown(this@MarkdownProcessor))

    private fun BlockQuote.toMarkdownBlockQuote(): MarkdownBlock.BlockQuote =
        MarkdownBlock.BlockQuote(processChildren(this))

    private fun Heading.toMarkdownHeadingOrNull(): MarkdownBlock.Heading? {
        if (level < 1 || level > 6) return null
        return MarkdownBlock.Heading(inlineContent = readInlineMarkdown(this@MarkdownProcessor), level = level)
    }

    private fun FencedCodeBlock.toMarkdownCodeBlockOrNull(): CodeBlock.FencedCodeBlock =
        CodeBlock.FencedCodeBlock(
            content = literal.removeSuffix("\n"),
            mimeType = MimeType.Known.fromMarkdownLanguageName(info),
        )

    private fun IndentedCodeBlock.toMarkdownCodeBlockOrNull(): CodeBlock.IndentedCodeBlock =
        CodeBlock.IndentedCodeBlock(literal.trimEnd('\n'))

    private fun BulletList.toMarkdownListOrNull(): ListBlock.UnorderedList? {
        val children = processListItems()
        if (children.isEmpty()) return null

        return ListBlock.UnorderedList(children = children, isTight = isTight, marker = marker)
    }

    private fun OrderedList.toMarkdownListOrNull(): ListBlock.OrderedList? {
        val children = processListItems()
        if (children.isEmpty()) return null

        return ListBlock.OrderedList(
            children = children,
            isTight = isTight,
            startFrom = markerStartNumber,
            delimiter = markerDelimiter,
        )
    }

    private fun CMListBlock.processListItems() = buildList {
        forEachChild { child ->
            if (child !is ListItem) return@forEachChild
            add(MarkdownBlock.ListItem(processChildren(child)))
        }
    }

    /**
     * Processes the children of a CommonMark [Node]. This function is public so that it can be accessed from
     * [MarkdownProcessorExtension]s, but should not be used in other scenarios.
     */
    @InternalJewelApi
    public fun processChildren(node: Node): List<MarkdownBlock> = buildList {
        node.forEachChild { child ->
            val parsedBlock = child.tryProcessMarkdownBlock()
            if (parsedBlock != null) {
                add(parsedBlock)
            }
        }
    }

    private fun Node.forEachChild(action: (Node) -> Unit) {
        var child = firstChild

        while (child != null) {
            action(child)
            child = child.next
        }
    }

    private fun Node.traverseAll(action: (Node) -> Unit) {
        action(this)
        forEachChild { child -> child.traverseAll(action) }
    }

    private fun HtmlBlock.toMarkdownHtmlBlockOrNull(): MarkdownBlock.HtmlBlock? {
        if (literal.isBlank()) return null
        return MarkdownBlock.HtmlBlock(literal.trimEnd('\n'))
    }

    /** Creates a copy of this [MarkdownProcessor] with the same properties, plus the provided [extension]. */
    @ExperimentalJewelApi
    public operator fun plus(extension: MarkdownProcessorExtension): MarkdownProcessor = withExtension(extension)

    /** Creates a copy of this [MarkdownProcessor] with the same properties, plus the provided [extension]. */
    public fun withExtension(extension: MarkdownProcessorExtension): MarkdownProcessor =
        MarkdownProcessor(extensions + extension, markdownMode, commonMarkParser)

    /** Store parsed blocks and previous text to find changed characters */
    private data class State(val text: String, val blocks: List<Block>)
}
