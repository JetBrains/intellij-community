// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.markdown.extensions

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.markdown.processing.html.HtmlElementConverter

/**
 * An extension for parsing HTML elements in Markdown which cannot be converted to a native Markdown element.
 *
 * Use it in your [MarkdownProcessorExtension] if it introduces a custom Markdown block and there exists an HTML element
 * that can be converted to that Markdown block. The notable example is Markdown tables. A table is a Markdown
 * extension, and there's an HTML element `table` which can be converted to a Markdown table.
 */
@ApiStatus.Experimental
@ExperimentalJewelApi
public interface MarkdownHtmlConverterExtension {
    /** Names of tags that this extension can convert. */
    @get:ApiStatus.Experimental @ExperimentalJewelApi public val supportedTags: Set<String>

    /**
     * Provides a converter for the tag with the given [tagName]. The extension is allowed to provide different
     * converters for different tags.
     */
    @ApiStatus.Experimental @ExperimentalJewelApi public fun provideConverter(tagName: String): HtmlElementConverter
}
