// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.markdown.extensions.github.tables

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.markdown.extensions.MarkdownBlockRendererExtension
import org.jetbrains.jewel.markdown.extensions.MarkdownRendererExtension
import org.jetbrains.jewel.markdown.rendering.MarkdownStyling

/**
 * An extension that provides a renderer for GFM tables.
 *
 * @param tableStyling The styling to use for the table.
 * @param rootStyling The root styling to use.
 */
@ApiStatus.Experimental
@ExperimentalJewelApi
public class GitHubTableRendererExtension(tableStyling: GfmTableStyling, rootStyling: MarkdownStyling) :
    MarkdownRendererExtension {
    override val blockRenderer: MarkdownBlockRendererExtension = GitHubTableBlockRenderer(rootStyling, tableStyling)
}
