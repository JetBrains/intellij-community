package org.jetbrains.jewel.markdown.extensions.autolink

import org.commonmark.ext.autolink.AutolinkExtension
import org.commonmark.parser.Parser.ParserExtension
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.markdown.extensions.MarkdownProcessorExtension

/**
 * A [MarkdownProcessorExtension] that adds support for auto-linking URLs and email addresses.
 *
 * For example, `https://www.jetbrains.com` will be rendered as a link.
 *
 * See the [GitHub Flavored Markdown specs](https://github.github.com/gfm/#autolinks-extension-).
 */
@ApiStatus.Experimental
@ExperimentalJewelApi
public object AutolinkProcessorExtension : MarkdownProcessorExtension {
    override val parserExtension: ParserExtension = AutolinkExtension.create() as ParserExtension
}
