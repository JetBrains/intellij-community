package org.jetbrains.jewel.markdown.rendering

import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.AnnotatedString.Builder
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import kotlin.math.max
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.markdown.InlineMarkdown
import org.jetbrains.jewel.markdown.extensions.MarkdownDelimitedInlineRendererExtension
import org.jetbrains.jewel.markdown.extensions.MarkdownRendererExtension

/**
 * A default implementation of [InlineMarkdownRenderer] that can be extended.
 *
 * This renderer handles standard CommonMark inline elements and is `open` to allow for customization of how specific
 * elements are rendered.
 *
 * @param rendererExtensions A list of [MarkdownRendererExtension]s for rendering custom inline nodes.
 */
@ApiStatus.Experimental
@ExperimentalJewelApi
public open class DefaultInlineMarkdownRenderer(rendererExtensions: List<MarkdownRendererExtension>) :
    InlineMarkdownRenderer {
    /** Extensions for rendering custom delimited inline nodes, such as `~strikethrough~`. */
    protected val delimitedNodeRendererExtensions: List<MarkdownDelimitedInlineRendererExtension> =
        rendererExtensions.mapNotNull { it.delimitedInlineRenderer }

    public override fun renderAsAnnotatedString(
        inlineMarkdown: Iterable<InlineMarkdown>,
        styling: InlinesStyling,
        enabled: Boolean,
        onUrlClicked: ((String) -> Unit)?,
    ): AnnotatedString = buildAnnotatedString {
        appendInlineMarkdownFrom(inlineMarkdown, styling, enabled, onUrlClicked, styling.textStyle)
    }

    /**
     * Appends a sequence of [InlineMarkdown] nodes to the [Builder], dispatching to the appropriate `render` function
     * based on the node type. This is the main recursive-like entry point for rendering children of a node.
     *
     * @param inlineMarkdown The sequence of nodes to process.
     * @param styling The styling rules to apply.
     * @param enabled The current enabled state.
     * @param onUrlClicked The callback for link clicks.
     * @param currentTextStyle The base text style to build upon for nested nodes.
     */
    private fun Builder.appendInlineMarkdownFrom(
        inlineMarkdown: Iterable<InlineMarkdown>,
        styling: InlinesStyling,
        enabled: Boolean,
        onUrlClicked: ((String) -> Unit)?,
        currentTextStyle: TextStyle,
    ) {
        for (child in inlineMarkdown) {
            when (child) {
                is InlineMarkdown.Text -> renderText(child, styling, enabled, currentTextStyle)
                is InlineMarkdown.Emphasis -> renderEmphasis(child, styling, enabled, onUrlClicked, currentTextStyle)
                is InlineMarkdown.StrongEmphasis ->
                    renderStrongEmphasis(child, styling, enabled, onUrlClicked, currentTextStyle)
                is InlineMarkdown.Link -> renderLink(child, styling, enabled, onUrlClicked, currentTextStyle)
                is InlineMarkdown.Code -> renderInlineCode(child, styling, enabled, currentTextStyle)
                is InlineMarkdown.HardLineBreak -> renderHardLineBreak(styling, currentTextStyle)
                is InlineMarkdown.SoftLineBreak -> renderSoftLineBreak(styling, currentTextStyle)
                is InlineMarkdown.HtmlInline -> renderInlineHtml(child, styling, enabled, currentTextStyle)
                is InlineMarkdown.Image -> renderImage(child, styling, enabled, currentTextStyle)

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

    /** Renders an [InlineMarkdown.Text] node by appending its content. */
    protected open fun Builder.renderText(
        node: InlineMarkdown.Text,
        styling: InlinesStyling,
        enabled: Boolean,
        currentTextStyle: TextStyle,
    ) {
        append(node.content)
    }

    /**
     * Renders an [InlineMarkdown.Emphasis] node (e.g., `*text*`). Merges the emphasis style with the current style and
     * processes child nodes.
     */
    protected open fun Builder.renderEmphasis(
        node: InlineMarkdown.Emphasis,
        styling: InlinesStyling,
        enabled: Boolean,
        onUrlClicked: ((String) -> Unit)?,
        currentTextStyle: TextStyle,
    ) {
        val combinedStyle = currentTextStyle.smartMerge(styling.emphasis, enabled)
        withStyles(combinedStyle, node) {
            appendInlineMarkdownFrom(it.inlineContent, styling, enabled, onUrlClicked, combinedStyle)
        }
    }

    /**
     * Renders an [InlineMarkdown.StrongEmphasis] node (e.g., `**text**`). Merges the strong emphasis style with the
     * current style and processes child nodes.
     */
    protected open fun Builder.renderStrongEmphasis(
        node: InlineMarkdown.StrongEmphasis,
        styling: InlinesStyling,
        enabled: Boolean,
        onUrlClicked: ((String) -> Unit)?,
        currentTextStyle: TextStyle,
    ) {
        val combinedStyle = currentTextStyle.smartMerge(styling.strongEmphasis, enabled)
        withStyles(combinedStyle, node) {
            appendInlineMarkdownFrom(it.inlineContent, styling, enabled, onUrlClicked, combinedStyle)
        }
    }

    /**
     * Renders an [InlineMarkdown.Link] node. If enabled, a clickable [LinkAnnotation] is attached. Otherwise, a
     * disabled style is applied.
     */
    protected open fun Builder.renderLink(
        node: InlineMarkdown.Link,
        styling: InlinesStyling,
        enabled: Boolean,
        onUrlClicked: ((String) -> Unit)?,
        currentTextStyle: TextStyle,
    ) {
        val index =
            if (enabled) {
                val destination = node.destination
                val link =
                    LinkAnnotation.Clickable(
                        tag = destination,
                        linkInteractionListener = { _ -> onUrlClicked?.invoke(destination) },
                        styles = currentTextStyle.smartMerge(styling.textLinkStyles, enabled = true),
                    )
                pushLink(link)
            } else {
                val combinedStyle = currentTextStyle.smartMerge(styling.linkDisabled, enabled = false)
                pushStyle(combinedStyle.toSpanStyle())
            }

        appendInlineMarkdownFrom(node.inlineContent, styling, enabled, onUrlClicked, currentTextStyle)
        pop(index)
    }

    /** Renders an [InlineMarkdown.Code] node by applying the inline code style. */
    protected open fun Builder.renderInlineCode(
        node: InlineMarkdown.Code,
        styling: InlinesStyling,
        enabled: Boolean,
        currentTextStyle: TextStyle,
    ) {
        val combinedStyle = currentTextStyle.smartMerge(styling.inlineCode, enabled)
        withStyles(combinedStyle, node) { append(it.content) }
    }

    /** Renders an [InlineMarkdown.HardLineBreak] node by appending a newline. */
    protected open fun Builder.renderHardLineBreak(styling: InlinesStyling, currentTextStyle: TextStyle) {
        appendLine()
    }

    /** Renders an [InlineMarkdown.SoftLineBreak] node by appending a space. */
    protected open fun Builder.renderSoftLineBreak(styling: InlinesStyling, currentTextStyle: TextStyle) {
        append(" ")
    }

    /** Renders an [InlineMarkdown.HtmlInline] node by appending its trimmed content. */
    protected open fun Builder.renderInlineHtml(
        node: InlineMarkdown.HtmlInline,
        styling: InlinesStyling,
        enabled: Boolean,
        currentTextStyle: TextStyle,
    ) {
        val combinedStyle = currentTextStyle.smartMerge(styling.inlineHtml, enabled)
        withStyles(combinedStyle, node) { append(it.content.trim()) }
    }

    /**
     * Renders an [InlineMarkdown.Image] node. The default behavior is to render the image's raw Markdown syntax as
     * fallback text, as images cannot be embedded directly into an [AnnotatedString]. To actually render the image, use
     * the [org.jetbrains.jewel.markdown.extensions.ImageRendererExtension].
     */
    protected open fun Builder.renderImage(
        node: InlineMarkdown.Image,
        styling: InlinesStyling,
        enabled: Boolean,
        currentTextStyle: TextStyle,
    ) {
        // Each image source corresponds to one rendered image.
        appendInlineContent(
            node.source,
            buildString {
                append(" ![")
                if (node.alt.isNotEmpty()) append(node.alt)
                append("](")
                append(node.source)
                if (!node.title.isNullOrBlank()) {
                    append(" \"")
                    append(node.title)
                    append("\"")
                }
                append(") ")
            },
        )
    }

    /**
     * A utility function that applies a [TextStyle] to a section of the [AnnotatedString.Builder], ensuring the style
     * is popped correctly after the [action] is executed.
     *
     * The `T` type parameter is needed to avoid issues with capturing lambdas that would prevent smart casting of the
     * `node` variable.
     */
    private inline fun <T : InlineMarkdown> Builder.withStyles(
        spanStyle: TextStyle,
        node: T,
        action: Builder.(T) -> Unit,
    ) {
        val popTo = pushStyle(spanStyle.toSpanStyle())
        action(node)
        pop(popTo)
    }

    /**
     * Merges the current [TextStyle]into the given [TextLinkStyles] using the [smartMerge] algorithm.
     *
     * This function smartly merges the [SpanStyle]s within the provided [TextLinkStyles] with the current [TextStyle].
     * The merge takes the [enabled] parameter into consideration, too. For each state (style, focusedStyle,
     * hoveredStyle, pressedStyle) within [TextLinkStyles]:
     * - If the style exists, it's smart-merged with the current [TextStyle].
     * - If the style doesn't exist, it's ignored.
     *
     * @param linkStyles The [TextLinkStyles] to merge into.
     * @param enabled Indicates if the text is enabled, affecting the merge behavior.
     * @return A new [TextLinkStyles] object with the merged styles.
     * @see smartMerge
     */
    private fun TextStyle.smartMerge(linkStyles: TextLinkStyles, enabled: Boolean) =
        TextLinkStyles(
            style = linkStyles.style?.let { spanStyle -> smartMerge(spanStyle, enabled).toSpanStyle() },
            focusedStyle = linkStyles.focusedStyle?.let { spanStyle -> smartMerge(spanStyle, enabled).toSpanStyle() },
            hoveredStyle = linkStyles.hoveredStyle?.let { spanStyle -> smartMerge(spanStyle, enabled).toSpanStyle() },
            pressedStyle = linkStyles.pressedStyle?.let { spanStyle -> smartMerge(spanStyle, enabled).toSpanStyle() },
        )

    /**
     * Merges the [TextStyle] into the provided [SpanStyle], applying a smart merging strategy.
     *
     * The logic is as follows:
     * - **Font Style**: If `other` has a non-normal font style, it's used. Otherwise, the current style's font style is
     *   used. This is to ensure that italic styles are preserved.
     * - **Font Weight**: If only one style has a non-null font weight, that weight is used. If both have non-null
     *   weights, the heavier weight is used, preventing any reduction in weight.
     * - **Color**: The color from `other` is used if it's specified, unless `enabled` is false. If `other`'s color is
     *   unspecified and `enabled` is true, the current style's color is used.
     * - **Platform Style**: The platform span style is merged, if available.
     * - **Other Properties**: All other properties (fontSize, fontFamily, etc.) are taken directly from `other`.
     *
     * @param other The [SpanStyle] to merge the current [TextStyle] into.
     * @param enabled Indicates if the text is enabled. If false, the color will be set to [Color.Unspecified],
     *   effectively hiding it.
     * @return A new [TextStyle] with the properties merged according to the logic.
     */
    private fun TextStyle.smartMerge(other: SpanStyle, enabled: Boolean): TextStyle {
        val otherFontWeight = other.fontWeight
        val thisFontWeight = fontWeight

        return merge(
            // We use the other's FontStyle (if any) when it's not just Normal, otherwise we keep
            // our own FontStyle. This preserves incoming Italic, since Markdown has no way to
            // reset it to Normal anyway.
            fontStyle =
                if (other.fontStyle != null && other.fontStyle != FontStyle.Normal) {
                    other.fontStyle
                } else {
                    fontStyle
                },
            // If only one FontWeight is non-null, we use that; if both are null, it's also null.
            // If they're both non-null, we take the highest one, since Markdown has no way to
            // decrease the weight of text.
            fontWeight =
                when {
                    otherFontWeight != null && thisFontWeight == null -> otherFontWeight
                    otherFontWeight == null && thisFontWeight != null -> thisFontWeight
                    otherFontWeight != null && thisFontWeight != null ->
                        FontWeight(max(thisFontWeight.weight, otherFontWeight.weight))

                    else -> null
                },
            // The color is taken from the other, unless it's unspecified, or enabled is false
            color = if (enabled) other.color.takeOrElse { color } else Color.Unspecified,
            // Everything else comes from other
            fontSize = other.fontSize,
            fontSynthesis = other.fontSynthesis,
            fontFamily = other.fontFamily,
            fontFeatureSettings = other.fontFeatureSettings,
            letterSpacing = other.letterSpacing,
            baselineShift = other.baselineShift,
            textGeometricTransform = other.textGeometricTransform,
            localeList = other.localeList,
            background = other.background,
            textDecoration = other.textDecoration,
            shadow = other.shadow,
            drawStyle = other.drawStyle,
            platformStyle =
                PlatformTextStyle(platformStyle?.spanStyle?.merge(other.platformStyle), platformStyle?.paragraphStyle),
        )
    }
}
