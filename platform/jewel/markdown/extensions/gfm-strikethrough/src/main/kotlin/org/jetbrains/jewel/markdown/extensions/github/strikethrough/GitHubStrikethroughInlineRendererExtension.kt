package org.jetbrains.jewel.markdown.extensions.github.strikethrough

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.markdown.InlineMarkdown
import org.jetbrains.jewel.markdown.extensions.MarkdownDelimitedInlineRendererExtension
import org.jetbrains.jewel.markdown.rendering.InlineMarkdownRenderer
import org.jetbrains.jewel.markdown.rendering.InlinesStyling

/**
 * An extension for [`MarkdownInlineRenderer`][org.jetbrains.jewel.markdown.rendering.InlineMarkdownRenderer] that
 * renders [GitHubStrikethroughNode]s into annotated strings.
 */
@ApiStatus.Experimental
@ExperimentalJewelApi
public object GitHubStrikethroughInlineRendererExtension : MarkdownDelimitedInlineRendererExtension {
    private val strikethroughSpanStyle = SpanStyle(textDecoration = TextDecoration.Companion.LineThrough)

    override fun canRender(node: InlineMarkdown.CustomDelimitedNode): Boolean = node is GitHubStrikethroughNode

    override fun render(
        node: InlineMarkdown.CustomDelimitedNode,
        inlineRenderer: InlineMarkdownRenderer,
        inlinesStyling: InlinesStyling,
        enabled: Boolean,
        onUrlClicked: ((String) -> Unit)?,
    ): AnnotatedString {
        val strikethroughNode = node as GitHubStrikethroughNode
        return buildAnnotatedString {
            withStyle(strikethroughSpanStyle) {
                append(
                    inlineRenderer.renderAsAnnotatedString(
                        inlineMarkdown = strikethroughNode.inlineContent,
                        styling = inlinesStyling,
                        enabled = enabled,
                        onUrlClicked = onUrlClicked,
                    )
                )
            }
        }
    }
}
