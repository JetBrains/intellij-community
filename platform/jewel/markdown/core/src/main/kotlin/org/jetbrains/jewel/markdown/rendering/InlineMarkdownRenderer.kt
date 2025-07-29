package org.jetbrains.jewel.markdown.rendering

import androidx.compose.ui.text.AnnotatedString
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.markdown.InlineMarkdown
import org.jetbrains.jewel.markdown.extensions.MarkdownRendererExtension

/**
 * Renders a sequence of [InlineMarkdown] elements into an [AnnotatedString].
 *
 * This renderer is responsible for handling inline-level Markdown elements like text, emphasis, links, and code spans.
 * It translates these elements into a single, styled `AnnotatedString` that can be displayed in a Compose UI.
 *
 * @see renderAsAnnotatedString
 */
@ApiStatus.Experimental
@ExperimentalJewelApi
public interface InlineMarkdownRenderer {
    /**
     * Renders a sequence of [InlineMarkdown] elements into a single [AnnotatedString], applying the provided [styling].
     *
     * @param inlineMarkdown The sequence of [InlineMarkdown] elements to be rendered.
     * @param styling The [InlinesStyling] that defines the visual appearance of the different inline elements.
     * @param enabled Controls the enabled state of the rendered content. When `false`, interactive elements like links
     *   will be visually disabled.
     * @param onUrlClicked A callback that will be invoked when a user clicks on a link. The clicked URL is passed as an
     *   argument.
     * @return An [AnnotatedString] containing the fully rendered and styled inline content.
     */
    public fun renderAsAnnotatedString(
        inlineMarkdown: Iterable<InlineMarkdown>,
        styling: InlinesStyling,
        enabled: Boolean,
        onUrlClicked: ((String) -> Unit)? = null,
    ): AnnotatedString

    /** Companion object for [InlineMarkdownRenderer]. */
    public companion object
}

/**
 * Creates a new instance of the default [InlineMarkdownRenderer].
 *
 * @param rendererExtensions A list of [MarkdownRendererExtension]s to support rendering custom inline Markdown nodes.
 * @return A new [DefaultInlineMarkdownRenderer] instance.
 */
@ApiStatus.Experimental
@ExperimentalJewelApi
public fun InlineMarkdownRenderer.Companion.create(
    rendererExtensions: List<MarkdownRendererExtension>
): InlineMarkdownRenderer = DefaultInlineMarkdownRenderer(rendererExtensions)
