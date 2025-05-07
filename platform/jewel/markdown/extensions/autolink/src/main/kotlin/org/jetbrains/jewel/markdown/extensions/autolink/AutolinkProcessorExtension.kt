package org.jetbrains.jewel.markdown.extensions.autolink

import org.commonmark.ext.autolink.AutolinkExtension
import org.commonmark.parser.Parser.ParserExtension
import org.jetbrains.jewel.markdown.extensions.MarkdownProcessorExtension

public object AutolinkProcessorExtension : MarkdownProcessorExtension {
    override val parserExtension: ParserExtension = AutolinkExtension.create() as ParserExtension
}
