// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.markdown.processing.html

import org.jetbrains.jewel.markdown.InlineMarkdown
import org.jetbrains.jewel.markdown.WithTextContent
import org.jetbrains.jewel.markdown.processing.MarkdownProcessor

internal class MarkdownHtmlInlinesConverter {
    internal fun convert(processor: MarkdownProcessor, inlines: List<InlineMarkdown>): List<InlineMarkdown> {
        val matchingPairs = inlines.findOuterMatchingPairs(processor)
        if (matchingPairs.isEmpty()) {
            return inlines
        }

        val result = mutableListOf<InlineMarkdown>()
        var currentInlineIndex = 0
        for ((metadata, openIndex, closeIndex) in matchingPairs) {
            while (currentInlineIndex < openIndex) {
                result.add(inlines[currentInlineIndex++])
            }

            val openHtmlInline = inlines[openIndex]
            if (openHtmlInline !is InlineMarkdown.HtmlInline) {
                return inlines
            }

            if (openIndex == closeIndex) {
                if (metadata.type != HtmlTagType.EMPTY) {
                    return inlines
                }

                when (metadata.tagName) {
                    "br" -> result.add(InlineMarkdown.HardLineBreak)
                    "img" -> {
                        val element = MarkdownHtmlNode.toHtmlElement(openHtmlInline.content) ?: continue
                        result.add(
                            InlineMarkdown.Image(
                                source = element.attr("src"),
                                title = element.attr("title").ifEmpty { null },
                                alt = element.attr("alt"),
                            )
                        )
                    }
                    else -> return inlines
                }
                currentInlineIndex++
                continue
            }

            // hardest part: handle container tags
            val htmlInlineContent = inlines.subList(openIndex + 1, closeIndex)
            when (metadata.tagName) {
                "i",
                "em" -> {
                    result.add(InlineMarkdown.Emphasis("_", convert(processor, htmlInlineContent)))
                }
                "b",
                "strong" -> {
                    result.add(InlineMarkdown.StrongEmphasis("**", convert(processor, htmlInlineContent)))
                }
                "code",
                "pre" -> {
                    // Preserve literal inner content (including nested HTML tags) inside inline code.
                    val text =
                        htmlInlineContent.joinToString("") { inline ->
                            when (inline) {
                                is InlineMarkdown.HtmlInline -> inline.content
                                is WithTextContent -> inline.content
                                else -> ""
                            }
                        }
                    result.add(InlineMarkdown.Code(text))
                }
                "a" -> {
                    val element = MarkdownHtmlNode.toHtmlElement(openHtmlInline.content) ?: return inlines
                    result.add(
                        InlineMarkdown.Link(
                            element.attr("href"),
                            element.attr("title").ifEmpty { null },
                            convert(processor, htmlInlineContent),
                        )
                    )
                }
                else -> {
                    val newInlines =
                        convertUsingExtensions(processor, openHtmlInline.content, metadata, htmlInlineContent)
                    if (newInlines != null) {
                        result.addAll(newInlines)
                    }
                }
            }
            currentInlineIndex = closeIndex + 1
        }

        while (currentInlineIndex < inlines.size) {
            result.add(inlines[currentInlineIndex++])
        }

        return result
    }

    private fun convertUsingExtensions(
        processor: MarkdownProcessor,
        content: String,
        metadata: HtmlInlineMetadata,
        htmlInlineContent: List<InlineMarkdown>,
    ): List<InlineMarkdown>? {
        val converter = processor.provideExtensionHtmlElementConverterFor(metadata.tagName) ?: return null
        val element = MarkdownHtmlNode.toMarkdownHtmlElement(content) ?: return null
        return converter.convertInlines(element) { convert(processor, htmlInlineContent) }
    }

    // Returns all the outer matchings that should be converted at the current recursion depth
    // (both empty and container tags)
    // "outer" means nested matchings are not counted;
    // they will be addressed recursively
    @Suppress("UNCHECKED_CAST")
    private fun List<InlineMarkdown>.findOuterMatchingPairs(processor: MarkdownProcessor): List<MatchingPair> {
        val htmlInlines =
            withIndex().filter { it.value is InlineMarkdown.HtmlInline }
                as List<IndexedValue<InlineMarkdown.HtmlInline>>
        val result = mutableListOf<MatchingPair>()
        var i = 0

        var currentOpenTag: HtmlInlineMetadata? = null
        var currentOpenTagIndex = 0
        // for cases like <i><i></i></i>, to count the 4th tag as the matching and not the 3rd
        var outerTagOccurrenceCount = 0

        while (i < htmlInlines.size) {
            val (index, htmlInline) = htmlInlines[i++]

            val currentTag = htmlInline.metadata(processor) ?: continue

            when (currentTag.type) {
                HtmlTagType.EMPTY -> {
                    // don't add if inside a container tag
                    if (currentOpenTag == null) {
                        result.add(MatchingPair(currentTag, index, index))
                    }
                }
                HtmlTagType.OPEN -> {
                    if (currentOpenTag == null) {
                        currentOpenTag = currentTag
                        currentOpenTagIndex = index
                        outerTagOccurrenceCount = 1
                    } else if (currentOpenTag.tagName == currentTag.tagName) {
                        outerTagOccurrenceCount++
                    }
                }
                HtmlTagType.CLOSE -> {
                    if (currentOpenTag == null) {
                        return emptyList()
                    }
                    if (currentOpenTag.tagName != currentTag.tagName) continue
                    outerTagOccurrenceCount--
                    if (outerTagOccurrenceCount > 0) continue
                    result.add(MatchingPair(currentOpenTag, currentOpenTagIndex, index))
                    currentOpenTag = null
                }
            }
        }
        return result
    }

    private data class MatchingPair(val metadata: HtmlInlineMetadata, val openIndex: Int, val closeIndex: Int)

    // "null" means "tag is not supported"
    private fun InlineMarkdown.HtmlInline.metadata(processor: MarkdownProcessor): HtmlInlineMetadata? {
        val allSupportedInlineTags = processor.allSupportedInlineTags()
        if (content.startsWith("</")) {
            val tagName = content.substringAfter("</").takeWhile { it.isLetter() }
            if (tagName !in allSupportedInlineTags) {
                return null
            }
            return HtmlInlineMetadata(content.substringAfter("</").substringBefore(">"), HtmlTagType.CLOSE)
        }
        val tagName = content.substringAfter("<").takeWhile { it.isLetter() }
        if (tagName in allSupportedInlineTags) {
            return HtmlInlineMetadata(tagName, HtmlTagType.OPEN)
        }
        if (tagName in supportedEmptyInlineTags) {
            return HtmlInlineMetadata(tagName, HtmlTagType.EMPTY)
        }
        return null
    }

    private fun MarkdownProcessor.allSupportedInlineTags() =
        supportedContainerInlineTags + htmlConverterExtensions.flatMap { it.supportedTags }

    private class HtmlInlineMetadata(val tagName: String, val type: HtmlTagType)

    private enum class HtmlTagType {
        EMPTY,
        OPEN,
        CLOSE,
    }

    private companion object {
        private val supportedContainerInlineTags = setOf("a", "b", "code", "em", "i", "pre", "strong")
        private val supportedEmptyInlineTags = setOf("br", "img")
    }
}
