package org.jetbrains.jewel.intui.markdown.standalone

import org.jetbrains.jewel.intui.markdown.standalone.styling.dark
import org.jetbrains.jewel.intui.markdown.standalone.styling.light
import org.jetbrains.jewel.markdown.extensions.MarkdownRendererExtension
import org.jetbrains.jewel.markdown.rendering.DefaultInlineMarkdownRenderer
import org.jetbrains.jewel.markdown.rendering.DefaultMarkdownBlockRenderer
import org.jetbrains.jewel.markdown.rendering.InlineMarkdownRenderer
import org.jetbrains.jewel.markdown.rendering.MarkdownBlockRenderer
import org.jetbrains.jewel.markdown.rendering.MarkdownStyling

public fun MarkdownBlockRenderer.Companion.light(
    styling: MarkdownStyling = MarkdownStyling.light(),
    rendererExtensions: List<MarkdownRendererExtension> = emptyList(),
    inlineRenderer: InlineMarkdownRenderer = DefaultInlineMarkdownRenderer(rendererExtensions),
): MarkdownBlockRenderer = DefaultMarkdownBlockRenderer(styling, rendererExtensions, inlineRenderer)

public fun MarkdownBlockRenderer.Companion.dark(
    styling: MarkdownStyling = MarkdownStyling.dark(),
    rendererExtensions: List<MarkdownRendererExtension> = emptyList(),
    inlineRenderer: InlineMarkdownRenderer = DefaultInlineMarkdownRenderer(rendererExtensions),
): MarkdownBlockRenderer = DefaultMarkdownBlockRenderer(styling, rendererExtensions, inlineRenderer)
