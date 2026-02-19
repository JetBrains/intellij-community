// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.markdown.processing.html

import org.commonmark.node.HtmlBlock as CMHtmlBlock
import org.commonmark.node.SourceSpan
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.markdown.processing.MarkdownProcessor
import org.jsoup.nodes.TextNode

private typealias JsoupParser = org.jsoup.parser.Parser

private typealias JsoupNode = org.jsoup.nodes.Node

private typealias JsoupElement = org.jsoup.nodes.Element

/** Intermediate representation of HTML nodes parsed from [org.commonmark.node.HtmlBlock]. */
@ApiStatus.Experimental
@ExperimentalJewelApi
public sealed interface MarkdownHtmlNode {
    /** Raw HTML content of the node. */
    @get:ApiStatus.Experimental @ExperimentalJewelApi public val htmlContent: String

    /**
     * Represents a text node. This class is used to encapsulate inline text elements when processing Markdown with
     * embedded HTML.
     *
     * @property htmlContent The raw text represented by the node.
     */
    @ApiStatus.Experimental
    @ExperimentalJewelApi
    public class Text(override val htmlContent: String) : MarkdownHtmlNode

    /**
     * Represents an HTML element node in the Markdown HTML processing hierarchy.
     *
     * An `Element` contains a tag name, attributes, and children nodes. It provides a way to model and process HTML
     * content in the context of Markdown parsing and translation.
     *
     * @property tag the tag name of the element, e.g., "div", "p", "span".
     * @property isBlock indicates whether the element is a block-level element or an inline element.
     * @property attributes a map of HTML attributes associated with the element (e.g., class, id, style).
     * @property children a list of child nodes representing the inner content of the element.
     * @property lineRange the range of lines in the source Markdown document that corresponds to this element.
     * @property htmlContent the raw HTML content represented by the node, including the opening and closing tags.
     */
    @ApiStatus.Experimental
    @ExperimentalJewelApi
    public class Element(
        public val tag: String,
        public val isBlock: Boolean,
        // will be needed very soon (alignment)
        @Suppress("unused") public val attributes: Map<String, String>,
        public val children: List<MarkdownHtmlNode>,
        public val lineRange: IntRange,
        override val htmlContent: String,
    ) : MarkdownHtmlNode

    public companion object {
        private val contextElement = JsoupElement("p")

        /**
         * It's possible that some HTML blocks have to be translated to more than one Markdown block. For example,
         * contents like:
         * ```
         * <p>Hee-</p>
         * Ho!
         * ```
         *
         * are parsed by commonmark as a single HTML block, while it should be two different Markdown blocks.
         */
        internal fun convertHtmlBlock(processor: MarkdownProcessor, htmlBlock: CMHtmlBlock): List<MarkdownHtmlNode> {
            val fragments = JsoupParser.parseFragment(htmlBlock.literal, contextElement, "")
            return fragments.mapNotNull {
                it.ownerDocument()?.outputSettings()?.apply {
                    prettyPrint(false)
                    outline(false)
                }
                it.toMarkdownHtmlElement(processor, htmlBlock.sourceSpans)
            }
        }

        internal fun toHtmlElement(content: String): JsoupElement? =
            JsoupParser.parseFragment(content, contextElement, "").firstOrNull() as? JsoupElement

        internal fun toMarkdownHtmlElement(content: String): MarkdownHtmlNode? =
            toHtmlElement(content)?.toMarkdownHtmlElement(MarkdownProcessor(), emptyList())

        private fun JsoupNode.toMarkdownHtmlElement(
            processor: MarkdownProcessor,
            sourceSpans: List<SourceSpan>,
        ): MarkdownHtmlNode? =
            when (this) {
                is TextNode -> Text(wholeText)
                is JsoupElement -> {
                    val children = generateHtmlElementsFromChildren(processor, sourceSpans)
                    val lines = sourceSpans.map { it.lineIndex }.ifEmpty { listOf(0) }
                    val linesRange = lines.first()..lines.last()
                    Element(
                        tagName(),
                        isBlock,
                        attributes().associate { it.key to it.value },
                        children,
                        linesRange,
                        outerHtml(),
                    )
                }
                else -> null
            }

        private fun JsoupNode.generateHtmlElementsFromChildren(
            processor: MarkdownProcessor,
            sourceSpans: List<SourceSpan>,
        ): List<MarkdownHtmlNode> {
            if (!processor.isScrollSyncEnabled || sourceSpans.isEmpty()) {
                return childNodes().mapNotNull { it.toMarkdownHtmlElement(processor, emptyList()) }
            }
            val text = outerHtml()
            var currentSpanIndex = 0
            val children =
                childNodes().mapNotNull { child ->
                    val childText = child.outerHtml()
                    if (childText.isNotBlank()) {
                        val (spans, indexOfLastSpan) =
                            sourceSpans.filterSpansThatCoverSubText(text, childText, currentSpanIndex)
                        currentSpanIndex = indexOfLastSpan
                        child.toMarkdownHtmlElement(processor, spans)
                    } else {
                        null
                    }
                }
            return children
        }

        private fun List<SourceSpan>.filterSpansThatCoverSubText(
            text: String,
            subText: String,
            startFromSpan: Int,
        ): SpansCoveringSubText {
            if (isEmpty()) return SpansCoveringSubText.empty()
            val globalStartIndex = first().inputIndex
            val currentStartIndex = this[startFromSpan].inputIndex
            val startIndexInText = currentStartIndex - globalStartIndex

            val entryInText = text.indexOf(subText, startIndexInText)
            if (entryInText == -1) return SpansCoveringSubText.empty()

            val globalEntryIndex = globalStartIndex + entryInText
            var lastSpanIndex = -1
            val result = filterIndexed { index, span ->
                val spanRange = span.inputIndex..<(span.inputIndex + span.length)
                val entryRange = globalEntryIndex..<(globalEntryIndex + subText.length)
                val spanCoversSubText = spanRange.intersects(entryRange)
                if (spanCoversSubText) {
                    lastSpanIndex = index
                }
                spanCoversSubText
            }
            return SpansCoveringSubText(result, lastSpanIndex)
        }

        private fun IntRange.intersects(other: IntRange): Boolean = first <= other.last && last >= other.first

        private data class SpansCoveringSubText(val spans: List<SourceSpan>, val indexOfLastSpan: Int) {
            companion object {
                fun empty() = SpansCoveringSubText(emptyList(), -1)
            }
        }
    }
}
