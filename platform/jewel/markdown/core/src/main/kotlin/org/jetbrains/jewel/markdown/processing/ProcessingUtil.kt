package org.jetbrains.jewel.markdown.processing

import org.commonmark.node.Code as CMCode
import org.commonmark.node.CustomNode as CMCustomNode
import org.commonmark.node.Emphasis as CMEmphasis
import org.commonmark.node.HardLineBreak as CMHardLineBreak
import org.commonmark.node.HtmlInline as CMHtmlInline
import org.commonmark.node.Image as CMImage
import org.commonmark.node.Link as CMLink
import org.commonmark.node.Node
import org.commonmark.node.SoftLineBreak as CMSoftLineBreak
import org.commonmark.node.StrongEmphasis as CMStrongEmphasis
import org.commonmark.node.Text as CMText
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.jewel.markdown.InlineMarkdown
import org.jetbrains.jewel.markdown.WithInlineMarkdown
import org.jetbrains.jewel.markdown.WithTextContent
import org.jetbrains.jewel.markdown.extensions.MarkdownProcessorExtension

@VisibleForTesting
internal fun Node.readInlineContent(
    markdownProcessor: MarkdownProcessor,
    extensions: List<MarkdownProcessorExtension>,
): List<InlineMarkdown> = buildList {
    var current = this@readInlineContent.firstChild
    while (current != null) {
        val inline = current.toInlineMarkdownOrNull(markdownProcessor, extensions)
        if (inline != null) add(inline)

        current = current.next
    }
}

@VisibleForTesting
internal fun Node.toInlineMarkdownOrNull(
    markdownProcessor: MarkdownProcessor,
    extensions: List<MarkdownProcessorExtension>,
) =
    when (this) {
        is CMText -> InlineMarkdown.Text(literal)
        is CMLink ->
            InlineMarkdown.Link(
                destination = destination,
                title = title,
                inlineContent = readInlineContent(markdownProcessor, extensions),
            )

        is CMEmphasis ->
            InlineMarkdown.Emphasis(
                delimiter = openingDelimiter,
                inlineContent = readInlineContent(markdownProcessor, extensions),
            )

        is CMStrongEmphasis ->
            InlineMarkdown.StrongEmphasis(openingDelimiter, readInlineContent(markdownProcessor, extensions))

        is CMCode -> InlineMarkdown.Code(literal)
        is CMHtmlInline -> InlineMarkdown.HtmlInline(literal)
        is CMImage -> {
            val inlineContent = readInlineContent(markdownProcessor, extensions)
            InlineMarkdown.Image(
                source = destination,
                alt = inlineContent.renderAsSimpleText().trim(),
                title = title,
                inlineContent = inlineContent,
            )
        }

        is CMHardLineBreak -> InlineMarkdown.HardLineBreak
        is CMSoftLineBreak -> InlineMarkdown.SoftLineBreak
        is CMCustomNode ->
            extensions
                .find { it.inlineProcessorExtension?.canProcess(this) == true }
                ?.inlineProcessorExtension
                ?.processInlineMarkdown(this, markdownProcessor)

        else -> error("Unexpected block $this")
    }

/** Used to render content as simple plain text, used when creating image alt text. */
internal fun List<InlineMarkdown>.renderAsSimpleText(): String = buildString {
    for (node in this@renderAsSimpleText) {
        when (node) {
            is WithInlineMarkdown -> append(node.inlineContent.renderAsSimpleText())
            is WithTextContent -> append(node.content)

            is InlineMarkdown.CustomNode -> {
                val textContent = node.contentOrNull()
                if (textContent != null) {
                    append(' ')
                    append(textContent)
                }
            }

            is InlineMarkdown.HardLineBreak -> append('\n')
            is InlineMarkdown.SoftLineBreak -> append(' ')
            else -> {
                // Ignore other nodes
            }
        }
    }
}
