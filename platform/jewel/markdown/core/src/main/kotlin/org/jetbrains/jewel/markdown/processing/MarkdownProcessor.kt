package org.jetbrains.jewel.markdown.processing

import org.commonmark.node.Block
import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.Code
import org.commonmark.node.CustomBlock
import org.commonmark.node.Document
import org.commonmark.node.Emphasis
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.HtmlBlock
import org.commonmark.node.HtmlInline
import org.commonmark.node.Image
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.Link
import org.commonmark.node.ListBlock
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.Text
import org.commonmark.node.ThematicBreak
import org.commonmark.parser.IncludeSourceSpans
import org.commonmark.parser.Parser
import org.commonmark.renderer.text.TextContentRenderer
import org.intellij.lang.annotations.Language
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.markdown.InlineMarkdown
import org.jetbrains.jewel.markdown.MarkdownBlock
import org.jetbrains.jewel.markdown.MarkdownBlock.CodeBlock
import org.jetbrains.jewel.markdown.MarkdownBlock.Heading.H1
import org.jetbrains.jewel.markdown.MarkdownBlock.Heading.H2
import org.jetbrains.jewel.markdown.MarkdownBlock.Heading.H3
import org.jetbrains.jewel.markdown.MarkdownBlock.Heading.H4
import org.jetbrains.jewel.markdown.MarkdownBlock.Heading.H5
import org.jetbrains.jewel.markdown.MarkdownBlock.Heading.H6
import org.jetbrains.jewel.markdown.MarkdownBlock.ListBlock.UnorderedList
import org.jetbrains.jewel.markdown.MimeType
import org.jetbrains.jewel.markdown.extensions.MarkdownProcessorExtension
import org.jetbrains.jewel.markdown.rendering.DefaultInlineMarkdownRenderer

/**
 * @param optimizeEdits Optional. Indicates whether the processing should only update the changed blocks
 * by keeping a previous state in memory. Default is `true`, use `false` for immutable data.
 */
@ExperimentalJewelApi
public class MarkdownProcessor(
    private val extensions: List<MarkdownProcessorExtension> = emptyList(),
    private val optimizeEdits: Boolean = true,
) {

    public constructor(vararg extensions: MarkdownProcessorExtension) : this(extensions.toList())

    private val commonMarkParser = Parser.builder()
        .let { builder ->
            builder.extensions(extensions.map(MarkdownProcessorExtension::parserExtension))
            if (optimizeEdits) {
                builder.includeSourceSpans(IncludeSourceSpans.BLOCKS)
            }
            builder.build()
        }

    private val textContentRenderer =
        TextContentRenderer.builder()
            .extensions(extensions.map { it.textRendererExtension })
            .build()

    private data class State(val lines: List<String>, val blocks: List<Block>, val indexes: List<Pair<Int, Int>>)

    private var currentState = State(emptyList(), emptyList(), emptyList())

    @TestOnly
    internal fun getCurrentIndexesInTest() = currentState.indexes

    /**
     * Parses a Markdown document, translating from CommonMark 0.31.2
     * to a list of [MarkdownBlock]. Inline Markdown in leaf nodes
     * is contained in [InlineMarkdown], which can be rendered
     * to an [androidx.compose.ui.text.AnnotatedString] by using
     * [DefaultInlineMarkdownRenderer.renderAsAnnotatedString].
     *
     * The contents of [InlineMarkdown] is equivalent to the original, but
     * normalized and simplified, and cleaned up as follows:
     * * Replace HTML entities with the corresponding character (escaped, if it
     *   is necessary)
     * * Inline link and image references and omit the reference blocks
     * * Use the destination as text for links when no text is set (escaped, if
     *   it is necessary)
     * * Normalize link titles to always use double quotes as enclosing
     *   character
     * * Normalize backticks in inline code runs
     * * Convert links in image descriptions to plain text
     * * Drop empty nodes with no visual representation (e.g., links with no
     *   text and destination)
     * * Remove unnecessary escapes
     * * Escape non-formatting instances of ``*_`~<>[]()!`` for clarity
     *
     * The contents of code blocks aren't transformed in any way. HTML blocks
     * get their outer whitespace trimmed, and so does inline HTML.
     *
     * @see DefaultInlineMarkdownRenderer
     */
    public fun processMarkdownDocument(@Language("Markdown") rawMarkdown: String): List<MarkdownBlock> {
        return if (!optimizeEdits) {
            textToBlocks(rawMarkdown)
        } else {
            processWithQuickEdits(rawMarkdown)
        }.mapNotNull { child ->
            child.tryProcessMarkdownBlock()
        }
    }

    @VisibleForTesting
    internal fun processWithQuickEdits(@Language("Markdown") rawMarkdown: String): List<Block> {
        val (previousLines, previousBlocks, previousIndexes) = currentState
        val newLines = rawMarkdown.lines()
        val nLinesDelta = newLines.size - previousLines.size
        // find a block prior to the first one changed in case some elements merge during the update
        var firstBlock = 0
        var firstLine = 0
        var currFirstBlock = 0
        var currFirstLine = 0
        outerLoop@ for ((i, spans) in previousIndexes.withIndex()) {
            val (_, end) = spans
            for (j in currFirstLine..end) {
                if (newLines[j] != previousLines[j]) {
                    break@outerLoop
                }
            }
            firstBlock = currFirstBlock
            firstLine = currFirstLine
            currFirstBlock = i + 1
            currFirstLine = end + 1
        }
        // find a block following the last one changed in case some elements merge during the update
        var lastBlock = previousBlocks.size
        var lastLine = previousLines.size
        var currLastBlock = lastBlock
        var currLastLine = lastLine
        outerLoop@ for ((i, spans) in previousIndexes.withIndex().reversed()) {
            val (begin, _) = spans
            for (j in begin until currLastLine) {
                if (previousLines[j] != newLines[j + nLinesDelta]) {
                    break@outerLoop
                }
            }
            lastBlock = currLastBlock
            lastLine = currLastLine
            currLastBlock = i
            currLastLine = begin
        }
        if (firstLine > lastLine + nLinesDelta) {
            // no change
            return previousBlocks
        }
        val updatedText = newLines.subList(firstLine, lastLine + nLinesDelta).joinToString("\n", postfix = "\n")
        val updatedBlocks: List<Block> = textToBlocks(updatedText)
        val updatedIndexes =
            updatedBlocks.map { node ->
                // special case for a bug where LinkReferenceDefinition is a Node,
                // but it takes over sourceSpans from the following Block
                if (node.sourceSpans.isEmpty()) {
                    node.sourceSpans = node.previous.sourceSpans
                }
                (node.sourceSpans.first().lineIndex + firstLine) to
                    (node.sourceSpans.last().lineIndex + firstLine)
            }
        val suffixIndexes = previousIndexes.subList(lastBlock, previousBlocks.size).map {
            (it.first + nLinesDelta) to (it.second + nLinesDelta)
        }
        val newBlocks = (
            previousBlocks.subList(0, firstBlock) +
                updatedBlocks +
                previousBlocks.subList(lastBlock, previousBlocks.size)
            )
        val newIndexes = previousIndexes.subList(0, firstBlock) + updatedIndexes + suffixIndexes
        currentState = State(newLines, newBlocks, newIndexes)
        return newBlocks
    }

    private fun textToBlocks(strings: String): List<Block> {
        val document =
            commonMarkParser.parse(strings) as? Document
                ?: error("This doesn't look like a Markdown document")
        val updatedBlocks: List<Block> =
            buildList {
                document.forEachChild { child ->
                    (child as? Block)?.let { add(it) }
                }
            }
        return updatedBlocks
    }

    private fun Node.tryProcessMarkdownBlock(): MarkdownBlock? =
        // Non-Block children are ignored
        when (this) {
            is BlockQuote -> toMarkdownBlockQuote()
            is Heading -> toMarkdownHeadingOrNull()
            is Paragraph -> toMarkdownParagraphOrNull()
            is FencedCodeBlock -> toMarkdownCodeBlockOrNull()
            is IndentedCodeBlock -> toMarkdownCodeBlockOrNull()
            is Image -> toMarkdownImageOrNull()
            is BulletList -> toMarkdownListOrNull()
            is OrderedList -> toMarkdownListOrNull()
            is ThematicBreak -> MarkdownBlock.ThematicBreak
            is HtmlBlock -> toMarkdownHtmlBlockOrNull()
            is CustomBlock -> {
                extensions.find { it.processorExtension.canProcess(this) }
                    ?.processorExtension?.processMarkdownBlock(this, this@MarkdownProcessor)
            }

            else -> null
        }

    private fun BlockQuote.toMarkdownBlockQuote(): MarkdownBlock.BlockQuote =
        MarkdownBlock.BlockQuote(processChildren(this))

    private fun Heading.toMarkdownHeadingOrNull(): MarkdownBlock.Heading? =
        when (level) {
            1 -> H1(contentsAsInlineMarkdown())
            2 -> H2(contentsAsInlineMarkdown())
            3 -> H3(contentsAsInlineMarkdown())
            4 -> H4(contentsAsInlineMarkdown())
            5 -> H5(contentsAsInlineMarkdown())
            6 -> H6(contentsAsInlineMarkdown())
            else -> null
        }

    private fun Paragraph.toMarkdownParagraphOrNull(): MarkdownBlock.Paragraph? {
        val inlineMarkdown = contentsAsInlineMarkdown()

        if (inlineMarkdown.isBlank()) return null
        return MarkdownBlock.Paragraph(inlineMarkdown)
    }

    private fun FencedCodeBlock.toMarkdownCodeBlockOrNull(): CodeBlock.FencedCodeBlock =
        CodeBlock.FencedCodeBlock(
            literal.trimEnd('\n'),
            MimeType.Known.fromMarkdownLanguageName(info),
        )

    private fun IndentedCodeBlock.toMarkdownCodeBlockOrNull(): CodeBlock.IndentedCodeBlock =
        CodeBlock.IndentedCodeBlock(literal.trimEnd('\n'))

    private fun Image.toMarkdownImageOrNull(): MarkdownBlock.Image? {
        if (destination.isBlank()) return null

        return MarkdownBlock.Image(destination.trim(), title.trim())
    }

    private fun BulletList.toMarkdownListOrNull(): UnorderedList? {
        val children = processListItems()
        if (children.isEmpty()) return null

        return UnorderedList(children, isTight, bulletMarker)
    }

    private fun OrderedList.toMarkdownListOrNull(): MarkdownBlock.ListBlock.OrderedList? {
        val children = processListItems()
        if (children.isEmpty()) return null

        return MarkdownBlock.ListBlock.OrderedList(children, isTight, startNumber, delimiter)
    }

    private fun ListBlock.processListItems() = buildList {
        forEachChild { child ->
            if (child !is ListItem) return@forEachChild
            add(MarkdownBlock.ListItem(processChildren(child)))
        }
    }

    public fun processChildren(node: Node): List<MarkdownBlock> = buildList {
        node.forEachChild { child ->
            val parsedBlock = child.tryProcessMarkdownBlock()
            if (parsedBlock != null) this.add(parsedBlock)
        }
    }

    private fun Node.forEachChild(action: (Node) -> Unit) {
        var child = firstChild

        while (child != null) {
            action(child)
            child = child.next
        }
    }

    private fun HtmlBlock.toMarkdownHtmlBlockOrNull(): MarkdownBlock.HtmlBlock? {
        if (literal.isBlank()) return null
        return MarkdownBlock.HtmlBlock(content = literal.trimEnd('\n'))
    }

    private fun Node.contentsAsInlineMarkdown() =
        InlineMarkdown(buildString { appendInlineMarkdownFrom(this@contentsAsInlineMarkdown) })

    private fun StringBuilder.appendInlineMarkdownFrom(
        node: Node,
        allowLinks: Boolean = true,
        ignoreNestedEmphasis: Boolean = false,
    ) {
        var child = node.firstChild

        while (child != null) {
            when (child) {
                is Text -> append(child.literal.escapeInlineMarkdownChars())
                is Image -> appendImage(child)

                is Emphasis -> {
                    append(child.openingDelimiter)
                    appendInlineMarkdownFrom(child, !ignoreNestedEmphasis)
                    append(child.closingDelimiter)
                }

                is StrongEmphasis -> {
                    append(child.openingDelimiter)
                    appendInlineMarkdownFrom(child)
                    append(child.closingDelimiter)
                }

                is Code -> append(child.literal.asInlineCodeString())
                is Link -> appendLink(child, allowLinks)

                is HardLineBreak -> appendLine()
                is SoftLineBreak -> append(' ')
                is HtmlInline -> append(child.literal.trim())
            }
            child = child.next
        }
    }

    private fun StringBuilder.appendImage(child: Image) {
        append("![")
        appendInlineMarkdownFrom(child, allowLinks = false)

        // Escape dangling backslashes at the end of the text
        val backSlashCount = takeLastWhile { it == '\\' }.length
        if (backSlashCount % 2 != 0) append('\\')

        append("](")
        append(child.destination.escapeLinkDestination())

        if (!child.title.isNullOrBlank()) {
            append(" \"")
            append(child.title.replace("\"", "\\\"").trim())
            append('"')
        }
        append(')')
    }

    private fun StringBuilder.appendLink(child: Link, allowLinks: Boolean) {
        val hasText = child.firstChild != null
        if (child.destination.isNullOrBlank() && !hasText) {
            // Ignore links with no destination and no text
            return
        }

        if (allowLinks) {
            append('[')

            if (hasText) {
                // Link text cannot contain links — just in case...
                appendInlineMarkdownFrom(child, allowLinks = false)

                // Escape dangling backslashes at the end of the text
                val backSlashCount = takeLastWhile { it == '\\' }.length
                if (backSlashCount % 2 != 0) append('\\')
            } else {
                // No text: use the destination
                append(child.destination.escapeLinkDestinationForUseInText())
            }

            append("](")
            append(child.destination.escapeLinkDestination())

            if (!child.title.isNullOrBlank()) {
                append(" \"")
                append(child.title.replace("\"", "\\\"").trim())
                append('"')
            }
            append(')')
        } else {
            append(plainTextContents(child))
        }
    }

    private fun String.escapeLinkDestinationForUseInText() =
        replace("[", "&#91;").replace("]", "&#93;")

    private fun String.escapeLinkDestination(): String {
        val escaped = replace(">", "//>").replace("(", "\\(").replace(")", "\\)")
        return if (any { it.isWhitespace() && it != '\n' }) "<$escaped>" else escaped
    }

    private fun String.escapeInlineMarkdownChars() =
        buildString(length) {
            var precedingBackslashesCount = 0
            var isNewLine = true

            for (char in this@escapeInlineMarkdownChars) {
                when (char) {
                    '\\' -> precedingBackslashesCount++
                    '\n' -> isNewLine = true
                    else -> {
                        val isUnescaped = (precedingBackslashesCount % 2) == 0
                        if (char in "*_~`<>[]()!" && (isNewLine || isUnescaped)) {
                            append('\\')
                        }

                        isNewLine = false
                        precedingBackslashesCount = 0
                    }
                }
                append(char)
            }
        }

    private fun String.asInlineCodeString(): String {
        // Base case: doesn't contain backticks
        if (!contains("`")) {
            return "`$this`"
        }

        var currentCount = 0
        var longestCount = 0

        // First, count the longest run of backticks in the string
        for (char in this) {
            if (char == '`') {
                currentCount++
            } else {
                if (currentCount > longestCount) {
                    longestCount = currentCount
                }
                currentCount = 0
            }
        }

        if (currentCount > longestCount) longestCount = currentCount

        // Then wrap it in n + 1 backticks to avoid early termination
        val backticks = "`".repeat(longestCount + 1)
        return "$backticks$this$backticks"
    }

    private fun InlineMarkdown.isBlank(): Boolean = content.isBlank()

    private fun plainTextContents(node: Node): String = textContentRenderer.render(node)
}
