package org.jetbrains.jewel.markdown.extensions.frontmatter

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.markdown.extensions.MarkdownBlockRendererExtension
import org.jetbrains.jewel.markdown.extensions.MarkdownRendererExtension

/**
 * An extension that provides a renderer for YAML front matter metadata blocks.
 *
 * @param styling The styling to use for the front matter table.
 */
@ApiStatus.Experimental
@ExperimentalJewelApi
public class FrontMatterRendererExtension(styling: FrontMatterStyling) : MarkdownRendererExtension {
    override val blockRenderer: MarkdownBlockRendererExtension = FrontMatterBlockRenderer(styling)
}
