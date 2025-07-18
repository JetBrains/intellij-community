package org.jetbrains.jewel.markdown.processing

import org.commonmark.node.Code as CMCode
import org.commonmark.node.Delimited
import org.commonmark.node.Emphasis as CMEmphasis
import org.commonmark.node.HardLineBreak as CMHardLineBreak
import org.commonmark.node.HtmlInline as CMHtmlInline
import org.commonmark.node.Image as CMImage
import org.commonmark.node.Link as CMLink
import org.commonmark.node.Node
import org.commonmark.node.SoftLineBreak as CMSoftLineBreak
import org.commonmark.node.StrongEmphasis as CMStrongEmphasis
import org.commonmark.node.Text as CMText
import org.commonmark.parser.beta.ParsedInline
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.util.JewelLogger
import org.jetbrains.jewel.markdown.InlineMarkdown
import org.jetbrains.jewel.markdown.WithInlineMarkdown
import org.jetbrains.jewel.markdown.WithTextContent

/**
 * Reads all supported child inline nodes into a list of [InlineMarkdown] nodes, using the provided [markdownProcessor]
 * (and its registered extensions).
 *
 * @param markdownProcessor Used to parse the inline contents as needed.
 * @return A list of the contents as parsed [InlineMarkdown].
 * @see toInlineMarkdownOrNull
 */
@ApiStatus.Experimental
@ExperimentalJewelApi
public fun Node.readInlineMarkdown(markdownProcessor: MarkdownProcessor): List<InlineMarkdown> = buildList {
    var current = this@readInlineMarkdown.firstChild
    while (current != null) {
        val inline = current.toInlineMarkdownOrNull(markdownProcessor)
        if (inline != null) add(inline)

        current = current.next
    }
}

/**
 * Converts this node to an [InlineMarkdown] node if possible, using the provided [markdownProcessor] (and its
 * registered extensions).
 *
 * @param markdownProcessor Used to parse the contents of this node, as needed.
 * @return The parsed [InlineMarkdown], or null if it is a custom node that can't be parsed by any of the extensions
 *   registered to [markdownProcessor].
 * @see readInlineMarkdown
 */
public fun Node.toInlineMarkdownOrNull(markdownProcessor: MarkdownProcessor): InlineMarkdown? =
    when (this) {
        is CMText -> InlineMarkdown.Text(literal)
        is CMLink ->
            InlineMarkdown.Link(
                destination = destination,
                title = title,
                inlineContent = readInlineMarkdown(markdownProcessor),
            )

        is CMEmphasis ->
            InlineMarkdown.Emphasis(delimiter = openingDelimiter, inlineContent = readInlineMarkdown(markdownProcessor))

        is CMStrongEmphasis -> InlineMarkdown.StrongEmphasis(openingDelimiter, readInlineMarkdown(markdownProcessor))

        is CMCode -> InlineMarkdown.Code(literal)
        is CMHtmlInline -> InlineMarkdown.HtmlInline(literal)
        is CMImage -> {
            val inlineContent = readInlineMarkdown(markdownProcessor)
            InlineMarkdown.Image(
                source = destination,
                alt = inlineContent.renderAsSimpleText().trim(),
                title = title,
                inlineContent = inlineContent,
            )
        }

        is CMHardLineBreak -> InlineMarkdown.HardLineBreak
        is CMSoftLineBreak -> InlineMarkdown.SoftLineBreak
        is Delimited ->
            markdownProcessor.delimitedInlineExtensions
                .find { it.canProcess(this) }
                ?.processDelimitedInline(this, markdownProcessor)
        is ParsedInline -> null // Unsupported — see JEWEL-747

        else -> error("Unexpected block $this")
    }

/** Used to render content as simple plain text, used when creating image alt text. */
internal fun List<InlineMarkdown>.renderAsSimpleText(): String = buildString {
    for (node in this@renderAsSimpleText) {
        when (node) {
            is WithInlineMarkdown -> append(node.inlineContent.renderAsSimpleText())
            is WithTextContent -> append(node.content)
            is InlineMarkdown.HardLineBreak -> append('\n')
            is InlineMarkdown.SoftLineBreak -> append(' ')
            else -> {
                JewelLogger.getInstance("MarkdownProcessingUtil")
                    .debug("Ignoring node ${node.javaClass.simpleName} for text rendering")
            }
        }
    }
}
