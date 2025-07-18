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

@ApiStatus.Experimental
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
        appendInlineMarkdownFrom(inlineMarkdown, styling, enabled, onUrlClicked, styling.textStyle)
    }

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

    protected open fun Builder.renderText(
        node: InlineMarkdown.Text,
        styling: InlinesStyling,
        enabled: Boolean,
        currentTextStyle: TextStyle,
    ) {
        append(node.content)
    }

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

    protected open fun Builder.renderInlineCode(
        node: InlineMarkdown.Code,
        styling: InlinesStyling,
        enabled: Boolean,
        currentTextStyle: TextStyle,
    ) {
        val combinedStyle = currentTextStyle.smartMerge(styling.inlineCode, enabled)
        withStyles(combinedStyle, node) { append(it.content) }
    }

    protected open fun Builder.renderHardLineBreak(styling: InlinesStyling, currentTextStyle: TextStyle) {
        appendLine()
    }

    protected open fun Builder.renderSoftLineBreak(styling: InlinesStyling, currentTextStyle: TextStyle) {
        append(" ")
    }

    protected open fun Builder.renderInlineHtml(
        node: InlineMarkdown.HtmlInline,
        styling: InlinesStyling,
        enabled: Boolean,
        currentTextStyle: TextStyle,
    ) {
        val combinedStyle = currentTextStyle.smartMerge(styling.inlineHtml, enabled)
        withStyles(combinedStyle, node) { append(it.content.trim()) }
    }

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

    // The T type parameter is needed to avoid issues with capturing lambdas
    // making smart cast of the child local variable impossible.
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
