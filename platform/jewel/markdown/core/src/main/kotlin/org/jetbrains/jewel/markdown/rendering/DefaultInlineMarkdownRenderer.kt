package org.jetbrains.jewel.markdown.rendering

import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.AnnotatedString.Builder
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import org.commonmark.renderer.text.TextContentRenderer
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.markdown.InlineMarkdown
import org.jetbrains.jewel.markdown.extensions.MarkdownProcessorExtension

@ExperimentalJewelApi
public open class DefaultInlineMarkdownRenderer(
    rendererExtensions: List<MarkdownProcessorExtension>,
) : InlineMarkdownRenderer {
    public constructor(vararg extensions: MarkdownProcessorExtension) : this(extensions.toList())

    private val plainTextRenderer =
        TextContentRenderer
            .builder()
            .extensions(rendererExtensions.map { it.textRendererExtension })
            .build()

    public override fun renderAsAnnotatedString(
        inlineMarkdown: Iterable<InlineMarkdown>,
        styling: InlinesStyling,
        enabled: Boolean,
        onUrlClicked: ((String) -> Unit)?,
    ): AnnotatedString =
        buildAnnotatedString {
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
                is InlineMarkdown.Text -> append(child.nativeNode.literal)

                is InlineMarkdown.Emphasis -> {
                    withStyles(styling.emphasis.withEnabled(enabled), child) {
                        appendInlineMarkdownFrom(
                            it.children,
                            styling,
                            enabled,
                        )
                    }
                }

                is InlineMarkdown.StrongEmphasis -> {
                    withStyles(
                        styling.strongEmphasis.withEnabled(enabled),
                        child,
                    ) { appendInlineMarkdownFrom(it.children, styling, enabled) }
                }

                is InlineMarkdown.Link -> {
                    withStyles(styling.link.withEnabled(enabled), child) {
                        if (enabled) {
                            val destination = it.nativeNode.destination
                            val link =
                                LinkAnnotation.Clickable(
                                    tag = destination,
                                    linkInteractionListener = { _ -> onUrlClicked?.invoke(destination) },
                                )
                            pushLink(link)
                        }
                        appendInlineMarkdownFrom(it.children, styling, enabled)
                    }
                }

                is InlineMarkdown.Code -> {
                    withStyles(styling.inlineCode.withEnabled(enabled), child) { append(it.nativeNode.literal) }
                }

                is InlineMarkdown.HardLineBreak -> appendLine()
                is InlineMarkdown.SoftLineBreak -> append(" ")

                is InlineMarkdown.HtmlInline -> {
                    if (styling.renderInlineHtml) {
                        withStyles(
                            styling.inlineHtml.withEnabled(enabled),
                            child,
                        ) { append(it.nativeNode.literal.trim()) }
                    }
                }

                is InlineMarkdown.Image -> {
                    appendInlineContent(
                        INLINE_IMAGE,
                        child.nativeNode.destination + "\n" + plainTextRenderer.render(child.nativeNode),
                    )
                }

                is InlineMarkdown.CustomNode -> error("InlineMarkdown.CustomNode render is not implemented")
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

    public companion object {
        internal const val INLINE_IMAGE = "inline_image"
    }
}
