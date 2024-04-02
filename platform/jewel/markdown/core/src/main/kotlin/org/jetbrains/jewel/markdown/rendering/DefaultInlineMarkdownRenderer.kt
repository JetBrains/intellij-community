package org.jetbrains.jewel.markdown.rendering

import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.AnnotatedString.Builder
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.UrlAnnotation
import androidx.compose.ui.text.buildAnnotatedString
import org.commonmark.renderer.text.TextContentRenderer
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.markdown.InlineMarkdown
import org.jetbrains.jewel.markdown.extensions.MarkdownProcessorExtension

@ExperimentalJewelApi
public open class DefaultInlineMarkdownRenderer(rendererExtensions: List<MarkdownProcessorExtension>) : InlineMarkdownRenderer {

    public constructor(vararg extensions: MarkdownProcessorExtension) : this(extensions.toList())

    private val plainTextRenderer =
        TextContentRenderer.builder()
            .extensions(rendererExtensions.map { it.textRendererExtension })
            .build()

    public override fun renderAsAnnotatedString(
        inlineMarkdown: List<InlineMarkdown>,
        styling: InlinesStyling,
    ): AnnotatedString =
        buildAnnotatedString {
            appendInlineMarkdownFrom(inlineMarkdown.iterator(), styling)
        }

    @OptIn(ExperimentalTextApi::class)
    private fun Builder.appendInlineMarkdownFrom(inlineMarkdown: Iterator<InlineMarkdown>, styling: InlinesStyling) {
        for (child in inlineMarkdown) {
            when (child) {
                is InlineMarkdown.Paragraph -> {
                    appendInlineMarkdownFrom(child.children, styling)
                }

                is InlineMarkdown.Text -> append(child.nativeNode.literal)

                is InlineMarkdown.Emphasis -> {
                    withStyles(styling.emphasis, child) { appendInlineMarkdownFrom(it.children, styling) }
                }

                is InlineMarkdown.StrongEmphasis -> {
                    withStyles(styling.strongEmphasis, child) { appendInlineMarkdownFrom(it.children, styling) }
                }

                is InlineMarkdown.Link -> {
                    withStyles(styling.link, child) {
                        pushUrlAnnotation(UrlAnnotation(it.nativeNode.destination))
                        appendInlineMarkdownFrom(it.children, styling)
                    }
                }

                is InlineMarkdown.Code -> {
                    withStyles(styling.inlineCode, child) { append(it.nativeNode.literal) }
                }

                is InlineMarkdown.HardLineBreak,
                is InlineMarkdown.SoftLineBreak,
                -> appendLine()

                is InlineMarkdown.HtmlInline -> {
                    if (styling.renderInlineHtml) {
                        withStyles(styling.inlineHtml, child) { append(it.nativeNode.literal.trim()) }
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
