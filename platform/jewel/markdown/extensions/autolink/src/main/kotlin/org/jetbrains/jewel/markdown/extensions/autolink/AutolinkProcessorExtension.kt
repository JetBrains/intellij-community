package org.jetbrains.jewel.markdown.extensions.autolink

import org.commonmark.ext.autolink.AutolinkExtension
import org.commonmark.parser.Parser.ParserExtension
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.markdown.extensions.MarkdownProcessorExtension

@ApiStatus.Experimental
@ExperimentalJewelApi
public object AutolinkProcessorExtension : MarkdownProcessorExtension {
    override val parserExtension: ParserExtension = AutolinkExtension.create() as ParserExtension
}
