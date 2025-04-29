package org.jetbrains.jewel.markdown.extensions.github.strikethrough

import org.jetbrains.jewel.markdown.extensions.MarkdownDelimitedInlineRendererExtension
import org.jetbrains.jewel.markdown.extensions.MarkdownRendererExtension

/**
 * A [MarkdownRendererExtension] that supports rendering
 * [org.jetbrains.jewel.markdown.InlineMarkdown.CustomDelimitedNode]s into [androidx.compose.ui.text.AnnotatedString]s.
 */
public object GitHubStrikethroughRendererExtension : MarkdownRendererExtension {
    public override val delimitedInlineRenderer: MarkdownDelimitedInlineRendererExtension =
        GitHubStrikethroughInlineRendererExtension
}
