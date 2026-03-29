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
import org.jetbrains.jewel.markdown.DimensionSize
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
public fun Node.readInlineMarkdown(markdownProcessor: MarkdownProcessor): List<InlineMarkdown> {
    val inlines = buildList {
        var current = this@readInlineMarkdown.firstChild
        while (current != null) {
            val (inline, next) = current.toInlineMarkdownOrNull(markdownProcessor)
            if (inline != null) add(inline)

            current = next
        }
    }
    return markdownProcessor.convertHtmlInlines(inlines)
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
@ExperimentalJewelApi
@ApiStatus.Experimental
public fun Node.toInlineMarkdownOrNull(markdownProcessor: MarkdownProcessor): Pair<InlineMarkdown?, Node?> {
    var next: Node? = this.next
    val inlineContent =
        when (this) {
            is CMText -> InlineMarkdown.Text(literal)
            is CMLink ->
                InlineMarkdown.Link(
                    destination = destination,
                    title = title,
                    inlineContent = readInlineMarkdown(markdownProcessor),
                )

            is CMEmphasis ->
                InlineMarkdown.Emphasis(
                    delimiter = openingDelimiter,
                    inlineContent = readInlineMarkdown(markdownProcessor),
                )

            is CMStrongEmphasis ->
                InlineMarkdown.StrongEmphasis(openingDelimiter, readInlineMarkdown(markdownProcessor))

            is CMCode -> InlineMarkdown.Code(literal)
            is CMHtmlInline -> InlineMarkdown.HtmlInline(literal)
            is CMImage -> {
                val inlineContent = readInlineMarkdown(markdownProcessor)
                val attrs = (next as? CMText)?.literal
                if (attrs?.startsWith('{') == true) {
                    val (width, height) = getImageSize(attrs.trim())
                    next = next.next
                    InlineMarkdown.Image(
                        source = destination,
                        alt = inlineContent.renderAsSimpleText().trim(),
                        title = title,
                        inlineContent = inlineContent,
                        width = width,
                        height = height,
                    )
                } else {
                    InlineMarkdown.Image(
                        source = destination,
                        alt = inlineContent.renderAsSimpleText().trim(),
                        title = title,
                        inlineContent = inlineContent,
                    )
                }
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
    return inlineContent to next
}

private fun getImageSize(attrs: String): Pair<DimensionSize?, DimensionSize?> =
    if (attrs.isValidImageAttributes()) parseImageAttributes(attrs) else (null to null)

private fun String.isValidImageAttributes(): Boolean =
    length >= 2 && first() == '{' && last() == '}' && indexOf('\n') < 0 && indexOf('\r') < 0

private fun parseImageAttributes(attrs: String): Pair<DimensionSize?, DimensionSize?> {
    val content = attrs.substring(1, attrs.lastIndex)
    var width: DimensionSize? = null
    var height: DimensionSize? = null

    imageSizeAttributeRegex.findAll(content).forEach { match ->
        val name = match.groupValues[1]
        val value = match.groups[2]?.value ?: match.groups[3]?.value ?: match.groups[4]?.value.orEmpty()

        when (name) {
            "width" -> width = value.parseMarkdownImageSize()
            "height" -> height = value.parseMarkdownImageSize()
        }
    }

    return width to height
}

private fun String.parseMarkdownImageSize(): DimensionSize? {
    val trimmed = trim()
    if (trimmed.isEmpty()) return null

    return when {
        trimmed.endsWith("px") -> trimmed.dropLast(2).toStrictIntOrNull()?.let(DimensionSize::Pixels)
        trimmed.endsWith("%") -> trimmed.dropLast(1).toStrictIntOrNull()?.let(DimensionSize::Percent)
        else -> trimmed.toStrictIntOrNull()?.let(DimensionSize::Pixels)
    }
}

private fun String.toStrictIntOrNull(): Int? {
    if (isEmpty() || any { !it.isDigit() }) return null
    return toIntOrNull()
}

private val imageSizeAttributeRegex = Regex("""(?:^|\s)(width|height)\s*=\s*(?:"([^"]*)"|'([^']*)'|(\S+))""")

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
