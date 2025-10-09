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

/** Intermediate representation of HTML elements parsed from [org.commonmark.node.HtmlBlock]. */
// intentionally not a MarkdownBlock -- it's just an intermediate representation
@ApiStatus.Experimental
@ExperimentalJewelApi
public sealed interface MarkdownHtmlElement {

    /** Raw HTML content of the element. */
    @get:ApiStatus.Experimental @ExperimentalJewelApi public val htmlContent: String

    @ApiStatus.Experimental
    @ExperimentalJewelApi
    public class Text(override val htmlContent: String) : MarkdownHtmlElement

    @ApiStatus.Experimental
    @ExperimentalJewelApi
    public class Element(
        public val tag: String,
        public val isBlock: Boolean,
        public val attributes: Map<String, String>,
        public val children: List<MarkdownHtmlElement>,
        public val lineRange: IntRange,
        override val htmlContent: String,
    ) : MarkdownHtmlElement

    public companion object {
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
        internal fun convertHtmlBlock(processor: MarkdownProcessor, htmlBlock: CMHtmlBlock): List<MarkdownHtmlElement> {
            val fragments = JsoupParser.parseFragment(htmlBlock.literal, contextElement, "")
            return fragments.mapNotNull { it.toMarkdownHtmlElement(processor, htmlBlock.sourceSpans) }
        }

        internal fun toHtmlElement(content: String): JsoupElement? =
            JsoupParser.parseFragment(content, contextElement, "").firstOrNull() as? JsoupElement

        internal fun toMarkdownHtmlElement(content: String): MarkdownHtmlElement? =
            toHtmlElement(content)?.toMarkdownHtmlElement(MarkdownProcessor(), emptyList())

        private fun JsoupNode.toMarkdownHtmlElement(
            processor: MarkdownProcessor,
            sourceSpans: List<SourceSpan>,
        ): MarkdownHtmlElement? =
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
        ): List<MarkdownHtmlElement> {
            if (!processor.isScrollSyncEnabled || sourceSpans.isEmpty()) {
                return childNodes().mapNotNull { it.toMarkdownHtmlElement(processor, emptyList()) }
            }
            val text = outerHtml()
            var currentSpanIndex = 0
            val children =
                childNodes().mapNotNull { child ->
                    val childText = child.outerHtml()
                    if (childText.isNotBlank()) {
                        val (spanIndex, spans) = sourceSpans.filterSubText(text, childText, currentSpanIndex)
                        currentSpanIndex = spanIndex
                        child.toMarkdownHtmlElement(processor, spans)
                    } else {
                        null
                    }
                }
            return children
        }

        private fun List<SourceSpan>.filterSubText(
            text: String,
            subText: String,
            startFromSpan: Int,
        ): Pair<Int, List<SourceSpan>> {
            if (isEmpty()) return 0 to emptyList()
            val globalStartIndex = first().inputIndex
            val currentStartIndex = this[startFromSpan].inputIndex
            val startIndexInText = currentStartIndex - globalStartIndex

            val entryInText = text.indexOf(subText, startIndexInText)
            if (entryInText == -1) return 0 to emptyList()

            val globalEntryIndex = globalStartIndex + entryInText
            var lastSpanIndex = -1
            val result = filterIndexed { index, span ->
                val spanCoversSubText =
                    span.inputIndex >= globalEntryIndex &&
                        span.inputIndex + span.length <= globalEntryIndex + subText.length
                if (spanCoversSubText) {
                    lastSpanIndex = index
                }
                spanCoversSubText
            }
            return lastSpanIndex + 1 to result
        }

        private val contextElement = JsoupElement("p")
    }
}
