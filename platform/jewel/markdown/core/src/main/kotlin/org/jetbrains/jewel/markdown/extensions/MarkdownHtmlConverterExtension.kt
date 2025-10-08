// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.markdown.extensions

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.markdown.processing.html.ElementConverter

@ApiStatus.Experimental
@ExperimentalJewelApi
public interface MarkdownHtmlConverterExtension {
    @ApiStatus.Experimental @ExperimentalJewelApi public fun provideConverter(tag: String): ElementConverter?
}
