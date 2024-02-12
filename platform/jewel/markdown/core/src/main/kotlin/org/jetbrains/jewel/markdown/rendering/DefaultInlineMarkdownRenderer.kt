package org.jetbrains.jewel.markdown.rendering

import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.AnnotatedString.Builder
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.UrlAnnotation
import androidx.compose.ui.text.buildAnnotatedString
import org.commonmark.node.Block
import org.commonmark.node.Code
import org.commonmark.node.Emphasis
import org.commonmark.node.HardLineBreak
import org.commonmark.node.HtmlInline
import org.commonmark.node.Image
import org.commonmark.node.Link
import org.commonmark.node.Node
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.Text
import org.commonmark.parser.Parser
import org.commonmark.renderer.text.TextContentRenderer
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.markdown.InlineMarkdown
import org.jetbrains.jewel.markdown.extensions.MarkdownProcessorExtension

@ExperimentalJewelApi
public open class DefaultInlineMarkdownRenderer(rendererExtensions: List<MarkdownProcessorExtension>) : InlineMarkdownRenderer {

    public constructor(vararg extensions: MarkdownProcessorExtension) : this(extensions.toList())

    private val commonMarkParser =
        Parser.builder().extensions(rendererExtensions.map { it.parserExtension }).build()

    private val plainTextRenderer =
        TextContentRenderer.builder()
            .extensions(rendererExtensions.map { it.textRendererExtension })
            .build()

    public override fun renderAsAnnotatedString(
        inlineMarkdown: InlineMarkdown,
        styling: InlinesStyling,
    ): AnnotatedString =
        buildAnnotatedString {
            val node = commonMarkParser.parse(inlineMarkdown.content)
            appendInlineMarkdownFrom(node, styling)
        }

    @OptIn(ExperimentalTextApi::class)
    private fun Builder.appendInlineMarkdownFrom(node: Node, styling: InlinesStyling) {
        var child = node.firstChild

        while (child != null) {
            when (child) {
                is Paragraph -> appendInlineMarkdownFrom(child, styling)
                is Image -> {
                    appendInlineContent(
                        INLINE_IMAGE,
                        child.destination + "\n" + plainTextRenderer.render(child),
                    )
                }

                is Text -> append(child.literal)
                is Emphasis -> {
                    withStyles(styling.emphasis, child) { appendInlineMarkdownFrom(it, styling) }
                }

                is StrongEmphasis -> {
                    withStyles(styling.strongEmphasis, child) { appendInlineMarkdownFrom(it, styling) }
                }

                is Code -> {
                    withStyles(styling.inlineCode, child) { append(it.literal) }
                }

                is Link -> {
                    withStyles(styling.link, child) {
                        pushUrlAnnotation(UrlAnnotation(it.destination))
                        appendInlineMarkdownFrom(it, styling)
                    }
                }

                is HardLineBreak,
                is SoftLineBreak,
                -> appendLine()

                is HtmlInline -> {
                    if (styling.renderInlineHtml) {
                        withStyles(styling.inlineHtml, child) { append(it.literal.trim()) }
                    }
                }

                is Block -> {
                    error("Only inline Markdown can be rendered to an AnnotatedString. Found: $child")
                }
            }
            child = child.next
        }
    }

    // The T type parameter is needed to avoid issues with capturing lambdas
    // making smart cast of the child local variable impossible.
    private inline fun <T : Node> Builder.withStyles(
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
