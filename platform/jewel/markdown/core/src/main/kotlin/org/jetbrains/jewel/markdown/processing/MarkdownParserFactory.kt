package org.jetbrains.jewel.markdown.processing

import org.commonmark.parser.IncludeSourceSpans
import org.commonmark.parser.Parser
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.markdown.extensions.MarkdownProcessorExtension

/**
 * Simplifies creating a [CommonMark `Parser`][Parser] while also supporting Jewel's [MarkdownProcessorExtension]s and
 * the `optimizeEdits` flag.
 */
@ApiStatus.Experimental
@ExperimentalJewelApi
public object MarkdownParserFactory {
    /**
     * Create a [CommonMark `Parser`][Parser] with the provided [extensions]. The parser's [Builder][Parser.Builder] can
     * be customized by providing a non-null [customizeBuilder] lambda.
     *
     * Make sure to provide the right value for [optimizeEdits], matching the one provided to the [MarkdownProcessor],
     * or this parser will not be set up correctly.
     *
     * @param optimizeEdits If true, sets up the [Parser] to allow for edits optimization in the [MarkdownProcessor].
     * @param extensions A list of [MarkdownProcessorExtension] to attach.
     * @param customizeBuilder Allows customizing the [Parser.Builder] before its [build()][Parser.Builder.build] method
     *   is called.
     */
    public fun create(
        optimizeEdits: Boolean,
        extensions: List<MarkdownProcessorExtension> = emptyList(),
        customizeBuilder: (Parser.Builder.() -> Parser.Builder)? = null,
    ): Parser =
        Parser.builder()
            .extensions(extensions.map(MarkdownProcessorExtension::parserExtension))
            .run {
                val builder =
                    if (optimizeEdits) {
                        includeSourceSpans(IncludeSourceSpans.BLOCKS)
                    } else {
                        this
                    }

                if (customizeBuilder != null) {
                    builder.customizeBuilder()
                } else {
                    builder
                }
            }
            .build()
}
