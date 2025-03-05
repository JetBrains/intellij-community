package org.jetbrains.jewel.markdown.rendering

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.AnnotatedString.Builder
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.markdown.InlineMarkdown
import org.jetbrains.jewel.markdown.extensions.MarkdownDelimitedInlineRendererExtension
import org.jetbrains.jewel.markdown.extensions.MarkdownRendererExtension

@ExperimentalJewelApi
public open class DefaultInlineMarkdownRenderer(rendererExtensions: List<MarkdownRendererExtension>) :
    InlineMarkdownRenderer {
    protected val delimitedNodeRendererExtensions: List<MarkdownDelimitedInlineRendererExtension> =
        rendererExtensions.mapNotNull { it.delimitedInlineRenderer }

    public override fun renderAsAnnotatedString(
        inlineMarkdown: Iterable<InlineMarkdown>,
        styling: InlinesStyling,
        enabled: Boolean,
        onUrlClicked: ((String) -> Unit)?,
    ): AnnotatedString = buildAnnotatedString {
        appendInlineMarkdownFrom(inlineMarkdown, styling, enabled, onUrlClicked)
    }

    private fun Builder.appendInlineMarkdownFrom(
        inlineMarkdown: Iterable<InlineMarkdown>,
        styling: InlinesStyling,
        enabled: Boolean,
        onUrlClicked: ((String) -> Unit)? = null,
    ) {
        for (child in inlineMarkdown) {
            when (child) {
                is InlineMarkdown.Text -> append(child.content)

                is InlineMarkdown.Emphasis -> {
                    withStyles(styling.emphasis.withEnabled(enabled), child) {
                        appendInlineMarkdownFrom(it.inlineContent, styling, enabled)
                    }
                }

                is InlineMarkdown.StrongEmphasis -> {
                    withStyles(styling.strongEmphasis.withEnabled(enabled), child) {
                        appendInlineMarkdownFrom(it.inlineContent, styling, enabled)
                    }
                }

                is InlineMarkdown.Link -> {
                    val index =
                        if (enabled) {
                            val destination = child.destination
                            val link =
                                LinkAnnotation.Clickable(
                                    tag = destination,
                                    linkInteractionListener = { _ -> onUrlClicked?.invoke(destination) },
                                    styles = styling.textLinkStyles,
                                )
                            pushLink(link)
                        } else {
                            pushStyle(styling.linkDisabled)
                        }
                    appendInlineMarkdownFrom(child.inlineContent, styling, enabled)
                    pop(index)
                }

                is InlineMarkdown.Code -> {
                    withStyles(styling.inlineCode.withEnabled(enabled), child) { append(it.content) }
                }

                is InlineMarkdown.HardLineBreak -> appendLine()
                is InlineMarkdown.SoftLineBreak -> append(" ")

                is InlineMarkdown.HtmlInline -> {
                    if (styling.renderInlineHtml) {
                        withStyles(styling.inlineHtml.withEnabled(enabled), child) { append(it.content.trim()) }
                    }
                }

                is InlineMarkdown.Image -> {
                    // TODO not supported yet — see JEWEL-746
                }

                is InlineMarkdown.CustomDelimitedNode -> {
                    val delimitedNodeRendererExtension =
                        delimitedNodeRendererExtensions.find { it.canRender(child) } ?: continue

                    append(
                        delimitedNodeRendererExtension.render(
                            node = child,
                            inlineRenderer = this@DefaultInlineMarkdownRenderer,
                            inlinesStyling = styling,
                            enabled = enabled,
                            onUrlClicked = onUrlClicked,
                        )
                    )
                }
            }
        }
    }

    private fun SpanStyle.withEnabled(enabled: Boolean): SpanStyle =
        if (enabled) {
            this
        } else {
            copy(color = Color.Unspecified)
        }

    // The T type parameter is needed to avoid issues with capturing lambdas
    // making smart cast of the child local variable impossible.
    private inline fun <T : InlineMarkdown> Builder.withStyles(
        spanStyle: SpanStyle,
        node: T,
        action: Builder.(T) -> Unit,
    ) {
        val popTo = pushStyle(spanStyle)

        action(node)

        pop(popTo)
    }
}
