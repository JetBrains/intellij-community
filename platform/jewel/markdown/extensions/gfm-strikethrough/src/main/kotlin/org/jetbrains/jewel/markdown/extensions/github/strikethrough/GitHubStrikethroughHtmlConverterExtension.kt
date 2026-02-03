// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.markdown.extensions.github.strikethrough

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.markdown.InlineMarkdown
import org.jetbrains.jewel.markdown.MarkdownBlock
import org.jetbrains.jewel.markdown.extensions.MarkdownHtmlConverterExtension
import org.jetbrains.jewel.markdown.processing.html.HtmlElementConverter
import org.jetbrains.jewel.markdown.processing.html.MarkdownHtmlNode

/**
 * A [MarkdownHtmlConverterExtension] that supports converting HTML strikethrough elements (`<s>`, `<strike>`, `<del>`)
 * into [GitHubStrikethroughNode]s.
 */
@ApiStatus.Experimental
@ExperimentalJewelApi
public object GitHubStrikethroughHtmlConverterExtension : MarkdownHtmlConverterExtension {
    override val supportedTags: Set<String> = setOf("s", "strike", "del")

    override fun provideConverter(tagName: String): HtmlElementConverter = GitHubStrikethroughHtmlElementConverter

    private object GitHubStrikethroughHtmlElementConverter : HtmlElementConverter {
        override fun convert(
            htmlElement: MarkdownHtmlNode.Element,
            convertChildren: MarkdownHtmlNode.Element.() -> List<MarkdownBlock>,
            convertInlines: (List<MarkdownHtmlNode>) -> List<InlineMarkdown>,
        ): MarkdownBlock? = null

        override fun convertInlines(
            element: MarkdownHtmlNode,
            convertSubInlines: () -> List<InlineMarkdown>,
        ): List<InlineMarkdown> {
            val strikethroughNode = GitHubStrikethroughNode("~~", convertSubInlines())
            return listOf(strikethroughNode)
        }
    }
}
