package org.jetbrains.jewel.intui.markdown.bridge

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.intui.markdown.bridge.styling.create
import org.jetbrains.jewel.markdown.extensions.MarkdownRendererExtension
import org.jetbrains.jewel.markdown.rendering.DefaultMarkdownBlockRenderer
import org.jetbrains.jewel.markdown.rendering.InlineMarkdownRenderer
import org.jetbrains.jewel.markdown.rendering.MarkdownBlockRenderer
import org.jetbrains.jewel.markdown.rendering.MarkdownStyling
import org.jetbrains.jewel.markdown.rendering.create

@ApiStatus.Experimental
@ExperimentalJewelApi
public fun MarkdownBlockRenderer.Companion.create(
    styling: MarkdownStyling = MarkdownStyling.create(),
    rendererExtensions: List<MarkdownRendererExtension> = emptyList(),
    inlineRenderer: InlineMarkdownRenderer = InlineMarkdownRenderer.create(rendererExtensions),
): MarkdownBlockRenderer = DefaultMarkdownBlockRenderer(styling, rendererExtensions, inlineRenderer)
