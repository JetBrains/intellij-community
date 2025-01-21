package org.jetbrains.jewel.markdown.extensions.images

import org.jetbrains.jewel.markdown.extensions.ImageRendererExtension
import org.jetbrains.jewel.markdown.extensions.MarkdownRendererExtension

/**
 * A [MarkdownRendererExtension] that supports rendering
 * [org.jetbrains.jewel.markdown.InlineMarkdown.CustomDelimitedNode]s into [androidx.compose.ui.text.AnnotatedString]s.
 */
public object Coil3ImagesRendererExtension : MarkdownRendererExtension {
    override val imageRendererExtension: ImageRendererExtension
        get() = Coil3ImagesRendererExtensionImpl
}
