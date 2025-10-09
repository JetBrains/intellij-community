// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.markdown.extensions

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.markdown.processing.html.HtmlElementConverter

@ApiStatus.Experimental
@ExperimentalJewelApi
public interface MarkdownHtmlConverterExtension {
    @get:ApiStatus.Experimental @ExperimentalJewelApi public val supportedTags: Set<String>

    // leaving the tagName here because, in theory, an extension can provide different converters for different tags
    @ApiStatus.Experimental @ExperimentalJewelApi public fun provideConverter(tagName: String): HtmlElementConverter
}
