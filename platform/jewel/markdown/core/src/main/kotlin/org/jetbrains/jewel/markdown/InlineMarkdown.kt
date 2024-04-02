package org.jetbrains.jewel.markdown

import org.commonmark.node.Node
import org.jetbrains.jewel.markdown.InlineMarkdown.Code
import org.jetbrains.jewel.markdown.InlineMarkdown.CustomNode
import org.jetbrains.jewel.markdown.InlineMarkdown.Emphasis
import org.jetbrains.jewel.markdown.InlineMarkdown.HardLineBreak
import org.jetbrains.jewel.markdown.InlineMarkdown.HtmlInline
import org.jetbrains.jewel.markdown.InlineMarkdown.Image
import org.jetbrains.jewel.markdown.InlineMarkdown.Link
import org.jetbrains.jewel.markdown.InlineMarkdown.SoftLineBreak
import org.jetbrains.jewel.markdown.InlineMarkdown.StrongEmphasis
import org.jetbrains.jewel.markdown.InlineMarkdown.Text
import org.commonmark.node.Code as CMCode
import org.commonmark.node.CustomNode as CMCustomNode
import org.commonmark.node.Emphasis as CMEmphasis
import org.commonmark.node.HardLineBreak as CMHardLineBreak
import org.commonmark.node.HtmlInline as CMHtmlInline
import org.commonmark.node.Image as CMImage
import org.commonmark.node.Link as CMLink
import org.commonmark.node.Paragraph as CMParagraph
import org.commonmark.node.SoftLineBreak as CMSoftLineBreak
import org.commonmark.node.StrongEmphasis as CMStrongEmphasis
import org.commonmark.node.Text as CMText

/**
 * A run of inline Markdown used as content for
 * [block-level elements][MarkdownBlock].
 */
public sealed interface InlineMarkdown {

    public val nativeNode: Node

    @JvmInline
    public value class Code(override val nativeNode: CMCode) : InlineMarkdown

    @JvmInline
    public value class CustomNode(override val nativeNode: CMCustomNode) : InlineMarkdown

    @JvmInline
    public value class Emphasis(override val nativeNode: CMEmphasis) : InlineMarkdown

    @JvmInline
    public value class HardLineBreak(override val nativeNode: CMHardLineBreak) : InlineMarkdown

    @JvmInline
    public value class HtmlInline(override val nativeNode: CMHtmlInline) : InlineMarkdown

    @JvmInline
    public value class Image(override val nativeNode: CMImage) : InlineMarkdown

    @JvmInline
    public value class Link(override val nativeNode: CMLink) : InlineMarkdown

    @JvmInline
    public value class Paragraph(override val nativeNode: CMParagraph) : InlineMarkdown

    @JvmInline
    public value class SoftLineBreak(override val nativeNode: CMSoftLineBreak) : InlineMarkdown

    @JvmInline
    public value class StrongEmphasis(override val nativeNode: CMStrongEmphasis) : InlineMarkdown

    @JvmInline
    public value class Text(override val nativeNode: CMText) : InlineMarkdown

    public val children: Iterator<InlineMarkdown>
        get() = object : Iterator<InlineMarkdown> {
            var current = this@InlineMarkdown.nativeNode.firstChild

            override fun hasNext(): Boolean = current != null

            override fun next(): InlineMarkdown =
                if (hasNext()) {
                    current.toInlineNode().also {
                        current = current.next
                    }
                } else {
                    throw NoSuchElementException()
                }
        }
}

public fun Node.toInlineNode(): InlineMarkdown = when (this) {
    is CMText -> Text(this)
    is CMLink -> Link(this)
    is CMEmphasis -> Emphasis(this)
    is CMStrongEmphasis -> StrongEmphasis(this)
    is CMCode -> Code(this)
    is CMHtmlInline -> HtmlInline(this)
    is CMImage -> Image(this)
    is CMHardLineBreak -> HardLineBreak(this)
    is CMSoftLineBreak -> SoftLineBreak(this)
    is CMCustomNode -> CustomNode(this)
    else -> error("Unexpected block $this")
}
